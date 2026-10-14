package io.horizontalsystems.core

import kotlinx.coroutines.flow.StateFlow

interface AppForegroundState {
    val inForeground: StateFlow<Boolean>
}
