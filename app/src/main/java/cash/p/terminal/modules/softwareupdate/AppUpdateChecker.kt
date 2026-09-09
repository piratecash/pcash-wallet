package cash.p.terminal.modules.softwareupdate

import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.managers.Version
import cash.p.terminal.modules.softwareupdate.domain.CheckAppUpdateUseCase
import cash.p.terminal.modules.softwareupdate.domain.InstallSource
import cash.p.terminal.modules.softwareupdate.domain.InstallSourceProvider
import cash.p.terminal.modules.softwareupdate.domain.ShouldAutoCheckUseCase
import cash.p.terminal.modules.softwareupdate.domain.UpdateStatus
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.ISystemInfoManager
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single source of truth for update state.
 *
 * - [updateAvailable] uses cached GitHub data only for non-Play installs. Google Play availability
 *   is never inferred from that cache.
 * - [updateState] carries the full result and is populated only after a network [checkNow].
 *
 * Concurrent checks are serialized by a [Mutex] so a resume-triggered check and a screen-open check
 * cannot publish results out of order.
 */
class AppUpdateChecker(
    private val checkAppUpdateUseCase: CheckAppUpdateUseCase,
    private val shouldAutoCheckUseCase: ShouldAutoCheckUseCase,
    private val systemInfoManager: ISystemInfoManager,
    private val localStorage: ILocalStorage,
    private val dispatcherProvider: DispatcherProvider,
    private val installSourceProvider: InstallSourceProvider,
) {
    private val _updateState = MutableStateFlow<UpdateStatus>(UpdateStatus.Unknown)
    val updateState: StateFlow<UpdateStatus> = _updateState.asStateFlow()

    private val _updateAvailable = MutableStateFlow(initialUpdateAvailable())
    val updateAvailable: StateFlow<Boolean> = _updateAvailable.asStateFlow()

    private val jobMutex = Mutex()
    private var inFlight: Deferred<Unit>? = null

    /** Runs a network check, coalescing concurrent callers into a single in-flight request. */
    suspend fun checkNow() {
        val deferred = jobMutex.withLock {
            inFlight?.takeIf { it.isActive } ?: dispatcherProvider.applicationScope.async {
                // CheckAppUpdateUseCase never throws (returns UpdateStatus.Error on failure), so
                // no crash guard is needed here; cancellation propagates as usual.
                val status = checkAppUpdateUseCase()
                _updateState.value = status
                _updateAvailable.value = updateAvailable(status)
            }.also { inFlight = it }
        }
        deferred.await()
    }

    fun checkIfNeeded() {
        if (!shouldAutoCheckUseCase()) return
        dispatcherProvider.applicationScope.launch { checkNow() }
    }

    private fun cachedUpdateAvailable(): Boolean {
        val known = localStorage.latestKnownVersion ?: return false
        return Version(known) > Version(systemInfoManager.appVersion)
    }

    private fun initialUpdateAvailable(): Boolean = when (installSourceProvider.installSource) {
        InstallSource.GOOGLE_PLAY -> false
        InstallSource.FDROID,
        InstallSource.OTHER,
        -> cachedUpdateAvailable()
    }

    private fun updateAvailable(status: UpdateStatus): Boolean =
        when (installSourceProvider.installSource) {
            InstallSource.GOOGLE_PLAY -> status is UpdateStatus.Available
            InstallSource.FDROID,
            InstallSource.OTHER,
            -> cachedUpdateAvailable()
        }
}
