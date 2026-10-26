package cash.p.terminal.shared.desktop

import cash.p.terminal.core.tryOrNull
import cash.p.terminal.manager.IConnectivityManager
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.NetworkInterface

interface NetworkInterfaceProbe {
    /** Names of the interfaces that are up and not loopback. */
    fun activeInterfaceNames(): Set<String>
}

/** An interface the OS fails to report on counts as down, so polling survives the error. */
object SystemNetworkInterfaceProbe : NetworkInterfaceProbe {
    override fun activeInterfaceNames(): Set<String> =
        tryOrNull { NetworkInterface.getNetworkInterfaces() }?.asSequence().orEmpty()
            .filter { tryOrNull { it.isUp && !it.isLoopback } == true }
            .mapTo(mutableSetOf()) { it.name }
}

/** The JVM has no connectivity callbacks, so the interfaces are polled; monitoring never stops. */
class DesktopConnectivityManager(
    private val probe: NetworkInterfaceProbe,
    private val dispatcherProvider: DispatcherProvider,
) : IConnectivityManager {

    private val _networkAvailabilityFlow = MutableSharedFlow<Boolean>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val networkAvailabilityFlow: SharedFlow<Boolean> = _networkAvailabilityFlow.asSharedFlow()

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _validatedNetworksFlow = MutableStateFlow<Set<Any>>(emptySet())
    override val validatedNetworksFlow: StateFlow<Set<Any>> = _validatedNetworksFlow.asStateFlow()

    override val torEnabled: Boolean = false

    init {
        update(forceEmit = true)
        dispatcherProvider.applicationScope.launch(dispatcherProvider.io) {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                refresh()
            }
        }
    }

    override fun refresh(): Boolean = update(forceEmit = false)

    override suspend fun refreshAndAwaitValidation(): Boolean = withContext(dispatcherProvider.io) { refresh() }

    override fun acquireMonitoringLease(): AutoCloseable = AutoCloseable {}

    private fun update(forceEmit: Boolean): Boolean {
        val interfaces = probe.activeInterfaceNames()
        val connected = interfaces.isNotEmpty()
        _validatedNetworksFlow.value = interfaces
        val wasConnected = _isConnected.getAndUpdate { connected }
        if (forceEmit || wasConnected != connected) {
            _networkAvailabilityFlow.tryEmit(connected)
        }
        return connected
    }

    private companion object {
        const val POLL_INTERVAL_MS = 5_000L
    }
}
