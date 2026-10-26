package io.horizontalsystems.core

import kotlinx.coroutines.flow.StateFlow

interface IBackgroundManager {
    val stateFlow: StateFlow<BackgroundManagerState>
    val inForeground: Boolean

    /** Raised on every return to the foreground, never later than [inForeground] turns true. */
    val foregroundEpoch: StateFlow<Int>
}

enum class BackgroundManagerState {
    Unknown,
    EnterForeground,
    EnterBackground,
    AllActivitiesDestroyed
}
