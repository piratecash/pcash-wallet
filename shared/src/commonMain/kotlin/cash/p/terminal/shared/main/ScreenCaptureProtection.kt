package cash.p.terminal.shared.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The window is kept out of screen capture while any registered screen is composed, as FLAG_SECURE on Android. */
class ScreenCaptureProtection {

    private val registrations = mutableSetOf<Any>()
    private val _isProtected = MutableStateFlow(false)
    val isProtected: StateFlow<Boolean> = _isProtected.asStateFlow()

    fun register(token: Any) = update { registrations += token }

    fun unregister(token: Any) = update { registrations -= token }

    private inline fun update(change: () -> Unit) {
        change()
        _isProtected.value = registrations.isNotEmpty()
    }
}

@Composable
fun ScreenCaptureProtectedEffect(protection: ScreenCaptureProtection) {
    DisposableEffect(protection) {
        val token = Any()
        protection.register(token)
        onDispose { protection.unregister(token) }
    }
}
