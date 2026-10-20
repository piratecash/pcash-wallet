package cash.p.terminal.modules.restoreaccount

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation3.runtime.NavBackStack
import cash.p.terminal.feature.wallet.WalletPlatformPages
import cash.p.terminal.modules.restoreaccount.restoremenu.RestoreMenuViewModel
import cash.p.terminal.modules.restoreaccount.restoremnemonicnonstandard.RestoreMnemonicNonStandardViewModel
import cash.p.terminal.modules.restoreaccount.restoremnemonicnonstandard.RestorePhraseNonStandard
import cash.p.terminal.modules.restoreaccount.restoreprivatekey.RestorePrivateKey
import cash.p.terminal.modules.restoreaccount.restoreprivatekey.RestorePrivateKeyViewModel
import cash.p.terminal.navigation.AppPages
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RestoreQrScannerAvailabilityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // Read during composition, so flipping it recomposes the screen under test.
    private var scannerAvailable by mutableStateOf(true)
    private val navigation = HSNavigation(NavBackStack<HSPage>())

    @Before
    fun setUp() {
        val platformPages = mockk<WalletPlatformPages> {
            every { isQrScannerAvailable } answers { scannerAvailable }
        }
        startKoin {
            modules(
                module {
                    single { platformPages }
                    single<AppPages> { mockk(relaxed = true) }
                    viewModel { RestorePrivateKeyViewModel(mockk(relaxed = true)) }
                    viewModel {
                        RestoreMnemonicNonStandardViewModel(mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))
                    }
                }
            )
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun restorePrivateKey_scannerUnavailable_hidesScanButton() {
        assertScanButtonFollowsAvailability {
            RestorePrivateKey(navigation, RestoreMenuViewModel(), RestoreViewModel(), {}, {})
        }
    }

    @Test
    fun restorePhraseNonStandard_scannerUnavailable_hidesScanButton() {
        assertScanButtonFollowsAvailability {
            RestorePhraseNonStandard(navigation, RestoreViewModel(), {}, {})
        }
    }

    private fun assertScanButtonFollowsAvailability(content: @Composable () -> Unit) {
        composeTestRule.setContent { ComposeAppTheme { content() } }
        val withScanner = clickableCount()

        scannerAvailable = false
        composeTestRule.waitForIdle()

        assertEquals(withScanner - 1, clickableCount())
    }

    private fun clickableCount() = composeTestRule.onAllNodes(hasClickAction()).fetchSemanticsNodes().size
}
