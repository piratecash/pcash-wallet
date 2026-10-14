package io.horizontalsystems.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DesktopAppForegroundState : AppForegroundState {
    private val _inForeground = MutableStateFlow(true)
    override val inForeground: StateFlow<Boolean> = _inForeground.asStateFlow()

    fun setInForeground(value: Boolean) {
        _inForeground.value = value
    }
}
