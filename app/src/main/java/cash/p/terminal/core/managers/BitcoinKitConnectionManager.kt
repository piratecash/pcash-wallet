package cash.p.terminal.core.managers

import cash.p.terminal.manager.IConnectivityManager
import co.touchlab.kermit.Logger
import io.horizontalsystems.bitcoincore.core.IConnectionManager
import io.horizontalsystems.bitcoincore.core.IConnectionManagerListener
import io.horizontalsystems.core.BackgroundManager
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

class BitcoinKitConnectionManager(
    private val connectivityManager: IConnectivityManager,
    private val backgroundManager: BackgroundManager,
    dispatcherProvider: DispatcherProvider,
) : IConnectionManager {
    private val logger = Logger.withTag("BitcoinKitConnectionManager")
    private val lock = Any()
    private val listeners = mutableListOf<WeakReference<IConnectionManagerListener>>()

    @Volatile
    private var connected = connectivityManager.isConnected.value

    override val isConnected: Boolean
        get() = connected

    init {
        dispatcherProvider.applicationScope.launch(dispatcherProvider.default) {
            connectivityManager.isConnected.collect { newValue ->
                updateConnection(newValue)
            }
        }
    }

    override fun addListener(listener: IConnectionManagerListener) {
        synchronized(lock) {
            cleanupListeners()
            if (listeners.none { it.get() === listener }) {
                listeners.add(WeakReference(listener))
                listener.onConnectionChange(connected)
            }
        }
    }

    override fun removeListener(listener: IConnectionManagerListener) {
        synchronized(lock) {
            listeners.removeAll { it.get() == null || it.get() === listener }
        }
    }

    override fun onEnterForeground() {
        if (!backgroundManager.inForeground) {
            updateConnection(connectivityManager.refresh())
        }
    }

    override fun onEnterBackground() = Unit

    private fun cleanupListeners() {
        listeners.removeAll { it.get() == null }
    }

    private fun updateConnection(newValue: Boolean) {
        synchronized(lock) {
            if (connected != newValue) {
                connected = newValue
                cleanupListeners()
                listeners.mapNotNull(WeakReference<IConnectionManagerListener>::get)
                    .forEach { listener ->
                        try {
                            listener.onConnectionChange(newValue)
                        } catch (error: Exception) {
                            logger.e(error) { "BitcoinKit connection listener failed" }
                        }
                    }
            }
        }
    }
}
