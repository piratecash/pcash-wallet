package cash.p.terminal.shared.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.awt.ComposeWindow
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.shared.main.ScreenCaptureProtection
import co.touchlab.kermit.Logger
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

private const val WDA_NONE = 0x0
private const val WDA_MONITOR = 0x1
private const val WDA_EXCLUDEFROMCAPTURE = 0x11

private val logger = Logger.withTag("WindowCaptureProtection")

internal interface DisplayAffinityNative {
    fun setDisplayAffinity(hwnd: Long, affinity: Int): Boolean
}

/** Windows only: macOS 15+ ignores capture exclusion and Linux has none. */
@Composable
fun WindowCaptureProtectionEffect(window: ComposeWindow, protection: ScreenCaptureProtection) {
    val native = remember { windowsDisplayAffinity() } ?: return
    LaunchedEffect(window, protection) {
        // The HWND does not exist until the window is shown.
        window.awaitDisplayable()
        protection.isProtected.collect { protected ->
            // Looked up on every change so that one failed lookup does not disable protection for good.
            val hwnd = window.frameHandle()
            if (hwnd == null) {
                logger.w { "Window handle is unavailable, protected=$protected" }
            } else {
                applyCaptureProtection(native, hwnd, protected)
            }
        }
    }
}

/** The frame's own HWND: ComposeWindow.windowHandle is the rendering layer's, not a top-level window. */
private fun ComposeWindow.frameHandle(): Long? = tryOrNull { Native.getWindowID(this) }?.takeIf { it != 0L }

/** Before Windows 10 2004 exclusion is unsupported: the window is captured as a black rectangle instead. */
internal fun applyCaptureProtection(native: DisplayAffinityNative, hwnd: Long, protected: Boolean) {
    val applied = if (protected) {
        native.setDisplayAffinity(hwnd, WDA_EXCLUDEFROMCAPTURE) || native.setDisplayAffinity(hwnd, WDA_MONITOR)
    } else {
        native.setDisplayAffinity(hwnd, WDA_NONE)
    }
    if (!applied) logger.w { "SetWindowDisplayAffinity failed, protected=$protected" }
}

private suspend fun ComposeWindow.awaitDisplayable() = suspendCancellableCoroutine { continuation ->
    val listener = object : WindowAdapter() {
        override fun windowOpened(e: WindowEvent) {
            removeWindowListener(this)
            if (continuation.isActive) continuation.resume(Unit)
        }
    }
    addWindowListener(listener)
    continuation.invokeOnCancellation { removeWindowListener(listener) }
    if (isDisplayable) {
        removeWindowListener(listener)
        continuation.resume(Unit)
    }
}

@Suppress("FunctionName")
internal interface User32 : Library {
    fun SetWindowDisplayAffinity(hWnd: Pointer, dwAffinity: Int): Boolean
}

private class User32DisplayAffinity(private val user32: User32) : DisplayAffinityNative {
    override fun setDisplayAffinity(hwnd: Long, affinity: Int) =
        user32.SetWindowDisplayAffinity(Pointer(hwnd), affinity)
}

private fun windowsDisplayAffinity(): DisplayAffinityNative? {
    if (!System.getProperty("os.name").orEmpty().startsWith("Windows")) return null
    val user32 = tryOrNull { Native.load("user32", User32::class.java) }
    if (user32 == null) logger.w { "user32 is unavailable: the window stays capturable" }
    return user32?.let(::User32DisplayAffinity)
}
