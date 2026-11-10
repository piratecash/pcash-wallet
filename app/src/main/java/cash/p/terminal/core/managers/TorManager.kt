package cash.p.terminal.core.managers

import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.ITorManager
import cash.p.terminal.core.tor.TorControlClient
import cash.p.terminal.core.tor.TorControlClient.Companion.BOOTSTRAP_DONE
import cash.p.terminal.core.tor.TorControlClient.Companion.SOCKS_HOST
import cash.p.terminal.core.tor.TorServiceGateway
import cash.p.terminal.manager.IConnectivityManager
import cash.p.terminal.modules.pin.core.UptimeProvider
import cash.p.terminal.modules.settings.security.tor.TorStatus
import co.touchlab.kermit.Logger
import io.horizontalsystems.core.BackgroundManagerState
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.IBackgroundManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket

/**
 * Routes the app's traffic to an in-process Tor through the JVM SOCKS properties, set once per process.
 * Tor listens on that port only while it carries traffic, so until then proxied connections fail fast.
 */
class TorManager(
    private val localStorage: ILocalStorage,
    private val gateway: TorServiceGateway,
    private val connectivityManager: IConnectivityManager,
    private val backgroundManager: IBackgroundManager,
    private val uptimeProvider: UptimeProvider,
    private val dispatcherProvider: DispatcherProvider,
) : ITorManager {

    private val logger = Logger.withTag("TorManager")

    // Confines all state below; blocking control calls run on io
    private val stateDispatcher = dispatcherProvider.default.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + stateDispatcher)

    private val _torStatusFlow = MutableStateFlow(TorStatus.Closed)
    override val torStatusFlow: StateFlow<TorStatus> = _torStatusFlow.asStateFlow()
    private val _bootstrapProgressFlow = MutableStateFlow<Int?>(null)
    override val bootstrapProgressFlow: StateFlow<Int?> = _bootstrapProgressFlow.asStateFlow()

    // Monero's wallet2 reads the proxy once when a wallet opens, so the port never changes in a process
    private val socksPort by lazy(::freeLoopbackPort)
    private var monitoringLease: AutoCloseable? = null
    private var connectJob: Job? = null
    private var backgroundSince: Long? = null
    private var lastValidatedNetworks: Set<Any> = emptySet()

    init {
        scope.launch(dispatcherProvider.io) { gateway.deleteLegacyFiles() }
        if (localStorage.torEnabled) {
            System.setProperty(SOCKS_HOST_PROPERTY, SOCKS_HOST)
            System.setProperty(SOCKS_PORT_PROPERTY, socksPort.toString())
            monitoringLease = connectivityManager.acquireMonitoringLease()
            scope.launch { gateway.events.collect { onTorLost(it) } }
            scope.launch { backgroundManager.stateFlow.collect { onAppStateChanged(it) } }
            scope.launch { connectivityManager.validatedNetworksFlow.collect { onValidatedNetworksChanged(it) } }
            start()
        }
    }

    override val isTorEnabled: Boolean
        get() = localStorage.torEnabled

    override fun setTorAsEnabled() {
        localStorage.torEnabled = true
        logger.i { "Tor enabled" }
    }

    override fun setTorAsDisabled() {
        localStorage.torEnabled = false
        logger.i { "Tor disabled" }
    }

    override fun start() {
        scope.launch { restart() }
    }

    // Makes no Tor or service call: the process restarts right after, and the in-process Tor dies with it
    override suspend fun stop() = withContext(stateDispatcher) {
        scope.coroutineContext.cancelChildren()
        System.clearProperty(SOCKS_HOST_PROPERTY)
        System.clearProperty(SOCKS_PORT_PROPERTY)
        monitoringLease?.close()
        _bootstrapProgressFlow.value = null
        _torStatusFlow.value = TorStatus.Closed
    }

    // Some services refuse part of the Tor exits, and Tor keeps a circuit for up to 10 minutes,
    // so a kit that failed on one exit keeps failing until its circuit changes
    override suspend fun newCircuits() {
        if (_torStatusFlow.value != TorStatus.Connected) return
        val signal = scope.launch { requestNewCircuits() }
        withTimeoutOrNull(NEWNYM_WAIT_MILLIS) { signal.join() }
    }

    private fun restart() {
        connectJob?.cancel()
        _torStatusFlow.value = TorStatus.Connecting
        connectJob = scope.launch { connect() }
    }

    private suspend fun connect() {
        try {
            val reconnecting = gateway.isTorRunning
            if (!reconnecting && !bindFreshService()) return fail("Cannot bind Tor service")
            val client = poll(CONTROL_WAIT_TRIES) {
                runInterruptible(dispatcherProvider.io) { gateway.openControlClient() }
            }
                ?: return fail("Tor control connection did not come up")
            client.call { authenticate() }

            if (reconnecting) {
                // Relay connections of a running Tor may be dead; requests must wait for fresh ones
                client.call { closeSocks() }
                client.call { setNetworkEnabled(false) }
                client.call { setNetworkEnabled(true) }
            }
            // Bootstrap can report 100% before a single circuit exists
            if (awaitBootstrap(client) && awaitBuiltCircuit(client)) {
                client.call { openSocks(socksPort) }
                _torStatusFlow.value = TorStatus.Connected
                logger.i { "Tor connected" }
            } else {
                fail("Tor bootstrap timed out")
            }
        } catch (e: IOException) {
            fail("Tor connect failed: ${e.message}")
        }
    }

    private suspend fun bindFreshService(): Boolean = withContext(dispatcherProvider.io) {
        gateway.unbind()
        gateway.bind()
    }

    private suspend fun awaitBootstrap(client: TorControlClient): Boolean = try {
        poll(BOOTSTRAP_WAIT_TRIES) {
            val progress = client.call { bootstrapProgress() }
            _bootstrapProgressFlow.value = progress
            progress.takeIf { it == BOOTSTRAP_DONE }
        } != null
    } finally {
        _bootstrapProgressFlow.value = null
    }

    private suspend fun awaitBuiltCircuit(client: TorControlClient): Boolean =
        poll(CIRCUIT_WAIT_TRIES) { client.call { hasBuiltCircuit() }.takeIf { it } } != null

    private suspend fun requestNewCircuits() {
        if (_torStatusFlow.value != TorStatus.Connected) return
        val client = gateway.controlClient() ?: return
        try {
            client.call { newCircuits() }
        } catch (e: IOException) {
            // A reconnect may have replaced the connection this signal was sent on
            val current = gateway.controlClient() === client
            if (current && _torStatusFlow.value == TorStatus.Connected) fail("New Tor circuits failed: ${e.message}")
        }
    }

    private suspend fun onTorLost(event: TorServiceGateway.Event) {
        connectJob?.cancel()
        fail("Tor lost: $event")
    }

    private fun onAppStateChanged(state: BackgroundManagerState) {
        when (state) {
            BackgroundManagerState.EnterBackground -> backgroundSince = uptimeProvider.uptime
            BackgroundManagerState.EnterForeground -> {
                val longBackground = backgroundSince
                    ?.let { uptimeProvider.uptime - it >= RECONNECT_AFTER_BACKGROUND_MILLIS } == true
                backgroundSince = null
                // Android cuts the network of background apps, which can leave Tor on dead relay connections
                val status = _torStatusFlow.value
                if (status == TorStatus.Failed || (status == TorStatus.Connected && longBackground)) restart()
            }
            else -> Unit
        }
    }

    // Relay connections made over the previous network are dead after a switch
    private fun onValidatedNetworksChanged(networks: Set<Any>) {
        // Re-registering the network callback on foreground briefly publishes an empty set
        if (networks.isEmpty()) return
        val switched = networks != lastValidatedNetworks
        val status = _torStatusFlow.value
        if (status == TorStatus.Failed || (status == TorStatus.Connected && switched)) restart()
        lastValidatedNetworks = networks
    }

    private suspend fun fail(reason: String) {
        logger.w { reason }
        _bootstrapProgressFlow.value = null
        _torStatusFlow.value = TorStatus.Failed
        if (!gateway.isTorRunning) return
        // A Tor left networking would keep retrying in the background; the next connect re-enables it
        try {
            gateway.controlClient()?.run {
                call { closeSocks() }
                call { setNetworkEnabled(false) }
            }
        } catch (e: IOException) {
            logger.w(e) { "Failed to stop Tor networking" }
        }
    }

    private suspend fun <T> TorControlClient.call(request: TorControlClient.() -> T): T = try {
        withTimeout(CONTROL_CALL_TIMEOUT_MILLIS) { runInterruptible(dispatcherProvider.io) { request() } }
    } catch (e: TimeoutCancellationException) {
        throw IOException("Tor control call timed out", e)
    } catch (e: IOException) {
        // jtorctl turns the interrupt of a cancelled call into an IOException
        currentCoroutineContext().ensureActive()
        throw e
    }

    private suspend inline fun <T : Any> poll(tries: Int, probe: () -> T?): T? {
        repeat(tries) {
            probe()?.let { return it }
            delay(POLL_INTERVAL_MILLIS)
        }
        return null
    }

    private fun freeLoopbackPort(): Int =
        ServerSocket(0, 1, InetAddress.getByName(SOCKS_HOST)).use { it.localPort }

    private companion object {
        const val SOCKS_HOST_PROPERTY = "socksProxyHost"
        const val SOCKS_PORT_PROPERTY = "socksProxyPort"
        const val RECONNECT_AFTER_BACKGROUND_MILLIS = 60_000L
        const val CONTROL_CALL_TIMEOUT_MILLIS = 10_000L
        const val NEWNYM_WAIT_MILLIS = 2_000L
        const val POLL_INTERVAL_MILLIS = 500L
        const val CONTROL_WAIT_TRIES = 60 // 30 s
        const val BOOTSTRAP_WAIT_TRIES = 240 // 2 min
        const val CIRCUIT_WAIT_TRIES = 120 // 1 min
    }
}
