package cash.p.terminal.shared.desktop.lock

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.shared.DesktopTestGraph
import cash.p.terminal.shared.TestWindowHost
import cash.p.terminal.shared.desktop.vault.DesktopStorageSession
import cash.p.terminal.shared.desktop.vault.DesktopVaultState
import cash.p.terminal.shared.main.DesktopHudMessenger
import cash.p.terminal.shared.main.DesktopNavHost
import cash.p.terminal.ui_compose.components.AppDialog
import cash.p.terminal.ui_compose.components.UrlOpener
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.mockk.mockk
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * While locked nothing of the app is left in the window: not a node, not a layer, not a composition.
 * Unlocking brings it back to the page, the ViewModels and the saved state the user left behind.
 */
@OptIn(ExperimentalTestApi::class)
class DesktopLockIsolationDesktopTest {

    private var appComposed = false
    private val createdViewModels = mutableListOf<TrackingViewModel>()
    private val systemLocale: Locale = Locale.getDefault()

    @BeforeTest
    fun setUp() {
        Locale.setDefault(Locale.ENGLISH)
        DesktopTestGraph.start()
    }

    @AfterTest
    fun tearDown() {
        Locale.setDefault(systemLocale)
        DesktopTestGraph.stop()
    }

    @Test
    fun locked_appWithAnOpenDialog_leavesOnlyTheLockScreen() = runDesktopComposeUiTest {
        var vaultState by mutableStateOf<DesktopVaultState>(unlocked())
        setContent { Root(vaultState) { AppWithDialog() } }
        onNodeWithText(DIALOG_ITEM).assertExists()
        assertTrue(appComposed)

        runOnIdle { vaultState = DesktopVaultState.Locked }
        waitForIdle()

        // Only a composition that is gone takes every layer with it, the library's popups included.
        assertFalse(appComposed, "the app must not stay composed under the lock")
        onNodeWithText(DIALOG_ITEM).assertDoesNotExist()
        onNodeWithText(UNLOCK_TITLE).assertExists()
        assertEquals(1, onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)

        // The one field left is the lock's own: typing into it is what the Unlock button waits for.
        onNodeWithText(UNLOCK).assertIsNotEnabled()
        onNode(hasSetTextAction()).performTextInput(PASSWORD)
        onNodeWithText(UNLOCK).assertIsEnabled()
    }

    @Test
    fun unlock_afterALock_returnsToThePageItsViewModelAndItsSavedState() = runDesktopComposeUiTest {
        var vaultState by mutableStateOf<DesktopVaultState>(unlocked())
        setContent { Root(vaultState) { navigation -> AppWithNavigation(navigation) } }
        onNodeWithText(OPEN_SECOND).performClick()
        onNodeWithText(KEEP_STATE).performClick()
        onNodeWithText(KEPT_STATE).assertExists()
        assertEquals(1, createdViewModels.size)

        runOnIdle { vaultState = DesktopVaultState.Locked }
        runOnIdle { vaultState = unlocked() }

        onNodeWithText(KEPT_STATE).assertExists()
        assertEquals(1, createdViewModels.size)
        assertFalse(createdViewModels.single().cleared)
    }

    @Composable
    private fun Root(vaultState: DesktopVaultState, appContent: @Composable (HSNavigation) -> Unit) {
        TestWindowHost {
            ComposeAppTheme {
                DesktopRootContent(
                    vaultState = vaultState,
                    localeTag = "en",
                    rootPage = { FirstPage() },
                    appContent = appContent,
                )
            }
        }
    }

    /** An app with a text field and a dialog of its own: two layers that must go with the app. */
    @Composable
    private fun AppWithDialog() {
        DisposableEffect(Unit) {
            appComposed = true
            onDispose { appComposed = false }
        }
        Box {
            Column {
                AppTextField()
                CompositionLocalProvider(
                    LocalNavigationEventDispatcherOwner provides rememberNavigationEventDispatcherOwner(parent = null)
                ) {
                    AppDialog(onDismissRequest = {}) {
                        body_leah(text = DIALOG_ITEM, modifier = Modifier.clickable { })
                        // Keeps the item away from the center, where the lock screen has its controls.
                        VSpacer(ITEM_OFFSET)
                    }
                }
            }
        }
    }

    @Composable
    private fun AppTextField() {
        var value by remember { mutableStateOf(FIELD_TEXT) }
        BasicTextField(value = value, onValueChange = { value = it })
    }

    @Composable
    private fun AppWithNavigation(navigation: HSNavigation) {
        DesktopNavHost(
            navigation = navigation,
            hudMessenger = remember { DesktopHudMessenger() },
            urlOpener = remember { NoUrlOpener },
        )
    }

    private inner class FirstPage : HSPage() {
        @Composable
        override fun GetContent(navigation: HSNavigation) {
            body_leah(
                text = OPEN_SECOND,
                modifier = Modifier.clickable { navigation.slideFromRight(SecondPage()) },
            )
        }
    }

    /** Stands in for a page the user left open: a ViewModel and state that must survive the lock. */
    private inner class SecondPage : HSPage() {
        @Composable
        override fun GetContent(navigation: HSNavigation) {
            viewModel<TrackingViewModel>(factory = trackingFactory())
            var kept by rememberSaveable { mutableStateOf("") }
            Column {
                body_leah(text = KEEP_STATE, modifier = Modifier.clickable { kept = KEPT_STATE })
                if (kept.isNotEmpty()) body_leah(text = kept)
            }
        }
    }

    private fun trackingFactory() = viewModelFactory {
        initializer { TrackingViewModel().also { createdViewModels += it } }
    }

    private fun unlocked() = DesktopVaultState.Unlocked(mockk<DesktopStorageSession>(relaxed = true))

    private class TrackingViewModel : ViewModel() {
        var cleared = false
            private set

        override fun onCleared() {
            cleared = true
        }
    }

    private object NoUrlOpener : UrlOpener {
        override fun open(url: String) = Unit
    }

    private companion object {
        const val DIALOG_ITEM = "dialog-item"
        const val FIELD_TEXT = "field-text"
        const val OPEN_SECOND = "open-second"
        const val KEEP_STATE = "keep-state"
        const val KEPT_STATE = "kept-state"
        const val PASSWORD = "Correct-horse1"
        const val UNLOCK_TITLE = "Enter your password"
        const val UNLOCK = "Unlock"
        val ITEM_OFFSET = 300.dp
    }
}
