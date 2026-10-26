package cash.p.terminal.shared.desktop.lock

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cash.p.terminal.feature.wallet.AppRestarter
import cash.p.terminal.shared.DesktopTestGraph
import cash.p.terminal.shared.desktop.DesktopAppRestarter
import cash.p.terminal.shared.TestWindowHost
import cash.p.terminal.shared.desktop.vault.DesktopStorageSession
import cash.p.terminal.shared.desktop.vault.DesktopVaultState
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.core.DesktopSettingsStore
import io.mockk.mockk
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.koin.compose.koinInject
import org.koin.core.context.GlobalContext

@OptIn(ExperimentalTestApi::class)
class DesktopRootDesktopTest {

    private val createdViewModels = mutableListOf<TrackingViewModel>()
    private var clicks = 0
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
    fun lock_thenUnlock_keepsTheScreenAndItsViewModel() = runDesktopComposeUiTest {
        var vaultState by mutableStateOf<DesktopVaultState>(unlocked())
        setContent { Root(vaultState) }
        onNodeWithText(MARKER).performClick()
        onNodeWithText(TAPPED).assertExists()

        runOnIdle { vaultState = DesktopVaultState.Locked }
        runOnIdle { vaultState = unlocked() }

        onNodeWithText(TAPPED).assertExists()
        assertEquals(1, createdViewModels.size)
        assertFalse(createdViewModels.single().cleared)
    }

    @Test
    fun locked_appUnderTheLockScreen_isGoneAndTakesNoClicks() = runDesktopComposeUiTest {
        var vaultState by mutableStateOf<DesktopVaultState>(unlocked())
        setContent { Root(vaultState) }
        val markerCenter = onNodeWithText(MARKER).fetchSemanticsNode().boundsInRoot.center

        runOnIdle { vaultState = DesktopVaultState.Locked }

        onNodeWithText(MARKER).assertDoesNotExist()
        clickAt(markerCenter)
        assertEquals(0, clicks)

        runOnIdle { vaultState = unlocked() }
        onNodeWithText(MARKER).assertExists()
        clickAt(markerCenter)
        assertEquals(1, clicks)
    }

    @Test
    fun noVault_afterAWipe_clearsTheViewModelsOfTheGoneSession() = runDesktopComposeUiTest {
        var vaultState by mutableStateOf<DesktopVaultState>(unlocked())
        setContent { Root(vaultState) }
        onNodeWithText(MARKER).assertExists()

        runOnIdle { vaultState = DesktopVaultState.NoVault }

        onNodeWithText(MARKER).assertDoesNotExist()
        assertTrue(createdViewModels.single().cleared)
    }

    @Test
    fun localeChange_recreatesTheAppAndClearsItsViewModels() = runDesktopComposeUiTest {
        var localeTag by mutableStateOf("en")
        val unlocked = unlocked()
        setContent { Root(unlocked, localeTag) }
        onNodeWithText(MARKER).performClick()

        runOnIdle { localeTag = "ru" }

        onNodeWithText(TAPPED).assertDoesNotExist()
        assertEquals(2, createdViewModels.size)
        assertTrue(createdViewModels.first().cleared)
        assertFalse(createdViewModels.last().cleared)
    }

    @Test
    fun localeChange_redrawsTheUnlockScreenInTheNewLanguage() = runDesktopComposeUiTest {
        var localeTag by mutableStateOf("en")
        setContent { Root(DesktopVaultState.Locked, localeTag) }
        onNodeWithText(PASSWORD_EN).assertExists()

        runOnIdle {
            // The language manager applies the locale before it reports the new tag.
            Locale.setDefault(Locale.forLanguageTag("ru"))
            localeTag = "ru"
        }

        onNodeWithText(PASSWORD_RU).assertExists()
    }

    @Test
    fun noVault_firstRun_showsTheIntroOnceBeforeCreatePassword() = runDesktopComposeUiTest {
        var vaultState by mutableStateOf<DesktopVaultState>(DesktopVaultState.NoVault)
        setContent { Root(vaultState) }
        onNodeWithText(INTRO_TITLE).assertExists()
        onNodeWithText(CREATE_TITLE).assertDoesNotExist()

        finishIntro()
        onNodeWithText(CREATE_TITLE).assertExists()

        // The password screen shown again, as after a restart, must not repeat the intro.
        runOnIdle { vaultState = DesktopVaultState.Locked }
        runOnIdle { vaultState = DesktopVaultState.NoVault }
        onNodeWithText(CREATE_TITLE).assertExists()
        onNodeWithText(INTRO_TITLE).assertDoesNotExist()
    }

    @Test
    fun noVault_afterAWipe_showsTheIntroAgain() = runDesktopComposeUiTest {
        var vaultState by mutableStateOf<DesktopVaultState>(DesktopVaultState.NoVault)
        setContent { Root(vaultState) }
        finishIntro()
        runOnIdle { vaultState = unlocked() }

        runOnIdle {
            // What the wipe does to the plain settings before it reports NoVault.
            GlobalContext.get().get<DesktopSettingsStore>().clearAll()
            vaultState = DesktopVaultState.NoVault
        }

        onNodeWithText(INTRO_TITLE).assertExists()
        onNodeWithText(CREATE_TITLE).assertDoesNotExist()
    }

    @Test
    fun restart_unlockedApp_startsOverWithFreshViewModels() = runDesktopComposeUiTest {
        setContent { Root(unlocked()) }
        onNodeWithText(MARKER).performClick()

        runOnIdle { GlobalContext.get().get<AppRestarter>().restart() }

        onNodeWithText(MARKER).assertExists()
        onNodeWithText(TAPPED).assertDoesNotExist()
        assertEquals(2, createdViewModels.size)
        assertTrue(createdViewModels.first().cleared)
    }

    @Test
    fun unlocked_pageShorterThanTheWindow_showsTheThemeBackgroundBelowIt() = runDesktopComposeUiTest {
        var themeBackground = Color.Unspecified
        setContent {
            TestWindowHost {
                ComposeAppTheme(darkTheme = true) {
                    themeBackground = ComposeAppTheme.colors.backgroundBase
                    DesktopRootContent(
                        vaultState = unlocked(),
                        localeTag = "en",
                        appGeneration = 0,
                        rootPage = { BlankPage() },
                        appContent = {},
                        wipeFailed = false,
                    )
                }
            }
        }

        val image = onRoot().captureToImage().toPixelMap()
        assertEquals(themeBackground, image[image.width / 2, image.height / 2])
    }

    @Composable
    private fun Root(vaultState: DesktopVaultState, localeTag: String = "en") {
        val appGeneration by koinInject<DesktopAppRestarter>().generation.collectAsStateWithLifecycle()
        TestWindowHost {
            ComposeAppTheme {
                DesktopRootContent(
                    vaultState = vaultState,
                    localeTag = localeTag,
                    appGeneration = appGeneration,
                    rootPage = { BlankPage() },
                    appContent = { AppContent() },
                    wipeFailed = false,
                )
            }
        }
    }

    /** Stands in for the app: a ViewModel, a click target and state that must survive a lock. */
    @Composable
    private fun AppContent() {
        viewModel<TrackingViewModel>(factory = trackingFactory())
        var tapped by rememberSaveable { mutableStateOf(false) }
        Box {
            Column {
                body_leah(
                    text = MARKER,
                    modifier = Modifier.clickable {
                        clicks++
                        tapped = true
                    },
                )
                if (tapped) body_leah(text = TAPPED)
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

    private companion object {
        const val MARKER = "app-marker"
        const val TAPPED = "app-tapped"
        const val PASSWORD_EN = "Password"
        const val PASSWORD_RU = "Пароль"
        const val INTRO_TITLE = "DEX Messenger"
        const val CREATE_TITLE = "Create a password"
    }
}
