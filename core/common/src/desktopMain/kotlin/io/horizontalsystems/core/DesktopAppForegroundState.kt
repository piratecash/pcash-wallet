package io.horizontalsystems.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** [inForeground] is window focus; the wallet lifecycle follows [windowMinimized] instead. */
class DesktopAppForegroundState : AppForegroundState {
    private val _inForeground = MutableStateFlow(true)
    override val inForeground: StateFlow<Boolean> = _inForeground.asStateFlow()

    private val _windowMinimized = MutableStateFlow(false)
    val windowMinimized: StateFlow<Boolean> = _windowMinimized.asStateFlow()

    fun setInForeground(value: Boolean) {
        _inForeground.value = value
    }

    fun setWindowMinimized(value: Boolean) {
        _windowMinimized.value = value
    }
}
