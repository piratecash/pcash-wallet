package cash.p.terminal.ui.compose

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import javax.swing.SwingUtilities
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SecureKeyboardEntryDesktopTest {

    private val native = CountingSecureInput()
    private val window = FakeWindowInfo()
    private var showFirst by mutableStateOf(true)
    private lateinit var platformNative: SecureInputNative

    @BeforeTest
    fun setUp() {
        flushEventQueue()
        platformNative = SecureKeyboardEntry.native
        SecureKeyboardEntry.native = native
    }

    @AfterTest
    fun tearDown() {
        // The composition is disposed by now; its deferred disable must reach the fake, not the real system.
        flushEventQueue()
        SecureKeyboardEntry.native = platformNative
    }

    @Test
    fun secureKeyboardEntry_fieldFocused_enablesOnce() = runComposeUiTest {
        setContent { Fields() }

        focus(FIRST)

        assertCalls(enabled = 1, disabled = 0)
    }

    @Test
    fun secureKeyboardEntry_focusMovesBetweenTwoProtectedFields_neverDisables() = runComposeUiTest {
        setContent { Fields() }

        focus(FIRST)
        focus(SECOND)
        assertCalls(enabled = 1, disabled = 0)

        focus(PLAIN)
        assertCalls(enabled = 1, disabled = 1)
    }

    @Test
    fun secureKeyboardEntry_fieldBlurred_disables() = runComposeUiTest {
        setContent { Fields() }

        focus(FIRST)
        focus(PLAIN)

        assertCalls(enabled = 1, disabled = 1)
    }

    @Test
    fun secureKeyboardEntry_windowLosesFocus_disablesAndReenablesOnReturn() = runComposeUiTest {
        setContent { Fields() }
        focus(FIRST)

        runOnIdle { window.isWindowFocused = false }
        settle()
        assertCalls(enabled = 1, disabled = 1)

        runOnIdle { window.isWindowFocused = true }
        settle()
        assertCalls(enabled = 2, disabled = 1)
    }

    @Test
    fun secureKeyboardEntry_fieldRemovedWhileFocused_disables() = runComposeUiTest {
        setContent { Fields() }
        focus(FIRST)

        runOnIdle { showFirst = false }
        settle()

        assertCalls(enabled = 1, disabled = 1)
    }

    @Test
    fun secureKeyboardEntry_unprotectedFieldFocused_doesNothing() = runComposeUiTest {
        setContent { Fields() }

        focus(PLAIN)

        assertCalls(enabled = 0, disabled = 0)
    }

    @Test
    fun secureKeyboardEntry_onAParentOfTheField_holdsWhileFocusIsInside() = runComposeUiTest {
        setContent { Fields() }

        focus(IN_FOCUS_GROUP)
        assertCalls(enabled = 1, disabled = 0)
        focus(PLAIN)
        assertCalls(enabled = 1, disabled = 1)

        focus(IN_WRAPPER)
        assertCalls(enabled = 2, disabled = 1)
        focus(PLAIN)
        assertCalls(enabled = 2, disabled = 2)
    }

    @Composable
    private fun Fields() {
        CompositionLocalProvider(LocalWindowInfo provides window) {
            Column {
                if (showFirst) {
                    BasicTextField("", {}, Modifier.testTag(FIRST).secureKeyboardEntry())
                }
                BasicTextField("", {}, Modifier.testTag(SECOND).secureKeyboardEntry())
                BasicTextField("", {}, Modifier.testTag(PLAIN))
                Box(Modifier.secureKeyboardEntry().focusGroup()) {
                    BasicTextField("", {}, Modifier.testTag(IN_FOCUS_GROUP))
                }
                Box(Modifier.secureKeyboardEntry()) {
                    BasicTextField("", {}, Modifier.testTag(IN_WRAPPER))
                }
            }
        }
    }

    private fun ComposeUiTest.focus(tag: String) {
        onNodeWithTag(tag).requestFocus()
        settle()
    }

    private fun ComposeUiTest.settle() {
        waitForIdle()
        flushEventQueue()
    }

    private fun flushEventQueue() = SwingUtilities.invokeAndWait {}

    private fun assertCalls(enabled: Int, disabled: Int) {
        assertEquals(enabled to disabled, native.enabled to native.disabled)
    }

    private class CountingSecureInput : SecureInputNative {
        var enabled = 0
        var disabled = 0

        override fun enable() {
            enabled++
        }

        override fun disable() {
            disabled++
        }
    }

    private class FakeWindowInfo : WindowInfo {
        override var isWindowFocused by mutableStateOf(true)
    }

    private companion object {
        const val FIRST = "first"
        const val SECOND = "second"
        const val PLAIN = "plain"
        const val IN_FOCUS_GROUP = "inFocusGroup"
        const val IN_WRAPPER = "inWrapper"
    }
}
