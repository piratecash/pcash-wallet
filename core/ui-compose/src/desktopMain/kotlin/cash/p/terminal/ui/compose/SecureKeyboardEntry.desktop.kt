package cash.p.terminal.ui.compose

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.platform.LocalWindowInfo
import cash.p.terminal.core.tryOrNull
import co.touchlab.kermit.Logger
import com.sun.jna.Library
import com.sun.jna.Native
import java.awt.EventQueue

actual fun Modifier.secureKeyboardEntry(): Modifier = this then SecureKeyboardEntryElement

private data object SecureKeyboardEntryElement : ModifierNodeElement<SecureKeyboardEntryNode>() {
    override fun create() = SecureKeyboardEntryNode()

    override fun update(node: SecureKeyboardEntryNode) = Unit
}

private class SecureKeyboardEntryNode :
    Modifier.Node(),
    FocusEventModifierNode,
    CompositionLocalConsumerModifierNode,
    ObserverModifierNode {

    private var fieldFocused = false
    private var windowFocused = false
    private var held = false

    override fun onAttach() = readWindowFocus()

    override fun onObservedReadsChanged() = readWindowFocus()

    override fun onFocusEvent(focusState: FocusState) {
        fieldFocused = focusState.hasFocus
        update()
    }

    override fun onDetach() = hold(false)

    private fun readWindowFocus() {
        observeReads { windowFocused = currentValueOf(LocalWindowInfo).isWindowFocused }
        update()
    }

    private fun update() = hold(isAttached && fieldFocused && windowFocused)

    private fun hold(value: Boolean) {
        if (value == held) return
        held = value
        if (value) SecureKeyboardEntry.acquire() else SecureKeyboardEntry.release()
    }
}

/** Process-wide owner of the macOS Secure Input state; touched only on the AWT event thread. */
internal object SecureKeyboardEntry {

    internal var native: SecureInputNative = platformSecureInputNative()

    private var holders = 0
    private var nativeOn = false

    fun acquire() {
        holders++
        if (!nativeOn) {
            native.enable()
            nativeOn = true
        }
    }

    fun release() {
        holders = (holders - 1).coerceAtLeast(0)
        // A focus move releases the old field before the new one acquires: never drop protection in between.
        if (holders == 0) EventQueue.invokeLater(::disableIfUnused)
    }

    private fun disableIfUnused() {
        if (holders == 0 && nativeOn) {
            native.disable()
            nativeOn = false
        }
    }
}

internal interface SecureInputNative {
    fun enable()
    fun disable()
}

private object NoSecureInput : SecureInputNative {
    override fun enable() = Unit

    override fun disable() = Unit
}

/** The system keeps a reference count: every enable must be matched by exactly one disable. */
private class CarbonSecureInput(private val carbon: Carbon) : SecureInputNative {
    override fun enable() = report("enable", carbon.EnableSecureEventInput())

    override fun disable() = report("disable", carbon.DisableSecureEventInput())

    private fun report(action: String, status: Int) {
        if (status != 0) logger.w { "Secure input $action failed with status $status" }
    }
}

@Suppress("FunctionName")
internal interface Carbon : Library {
    fun EnableSecureEventInput(): Int
    fun DisableSecureEventInput(): Int
}

private val logger = Logger.withTag("SecureKeyboardEntry")

private fun platformSecureInputNative(): SecureInputNative {
    if (!System.getProperty("os.name").orEmpty().startsWith("Mac")) return NoSecureInput
    val carbon = tryOrNull { Native.load("Carbon", Carbon::class.java) }
    if (carbon == null) logger.w { "Carbon is unavailable: secure keyboard entry stays off" }
    return carbon?.let(::CarbonSecureInput) ?: NoSecureInput
}
