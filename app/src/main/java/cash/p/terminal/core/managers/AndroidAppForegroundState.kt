package cash.p.terminal.core.managers

import io.horizontalsystems.core.AppForegroundState
import io.horizontalsystems.core.BackgroundManager
import io.horizontalsystems.core.BackgroundManagerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn

class AndroidAppForegroundState(backgroundManager: BackgroundManager) : AppForegroundState {

    override val inForeground: StateFlow<Boolean> = backgroundManager.stateFlow
        .mapNotNull { it.toForegroundOrNull() }
        .stateIn(
            CoroutineScope(SupervisorJob() + Dispatchers.Default),
            SharingStarted.Eagerly,
            backgroundManager.inForeground
        )
}

// EnterBackground can be conflated away by the StateFlow, leaving only AllActivitiesDestroyed.
internal fun BackgroundManagerState.toForegroundOrNull(): Boolean? =
    when (this) {
        BackgroundManagerState.EnterForeground -> true
        BackgroundManagerState.EnterBackground,
        BackgroundManagerState.AllActivitiesDestroyed -> false
        BackgroundManagerState.Unknown -> null
    }
