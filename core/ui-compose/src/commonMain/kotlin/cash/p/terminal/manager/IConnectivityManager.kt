package cash.p.terminal.manager

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface IConnectivityManager {
    val isConnected: StateFlow<Boolean>

    /** Emits every change of [isConnected], plus the state at each forced refresh. */
    val networkAvailabilityFlow: SharedFlow<Boolean>

    /** Changes whenever the set of validated networks does; the elements are opaque platform handles. */
    val validatedNetworksFlow: StateFlow<Set<Any>>
    val torEnabled: Boolean
    fun refresh(): Boolean

    /** Waits until [isConnected] reflects the system's current state. */
    suspend fun refreshAndAwaitValidation(): Boolean

    /** Keeps connectivity current while held, also in the background. */
    fun acquireMonitoringLease(): AutoCloseable
}
