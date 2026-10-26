package cash.p.terminal.shared.main

import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.navigation3.runtime.NavBackStack
import cash.p.terminal.core.AppearanceSettingsStore
import cash.p.terminal.entities.LaunchPage
import cash.p.terminal.modules.backuplocal.fullbackup.BackupManagerPage
import cash.p.terminal.modules.blockchainsettings.BlockchainSettingsPage
import cash.p.terminal.modules.manageaccounts.ManageAccountsModule
import cash.p.terminal.modules.manageaccounts.ManageAccountsPage
import cash.p.terminal.modules.premium.about.AboutPremiumPage
import cash.p.terminal.modules.settings.appearance.AppearancePage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.shared.DesktopTestGraph
import cash.p.terminal.shared.PcashApp
import cash.p.terminal.shared.TestWindowHost
import cash.p.terminal.shared.desktop.session.mnemonicAccount
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.ActiveAccountState
import cash.p.terminal.wallet.IAccountManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class DesktopMainPageDesktopTest {

    private val systemLocale: Locale = Locale.getDefault()
    private val activeAccountState = MutableStateFlow<ActiveAccountState>(ActiveAccountState.NotLoaded)
    private val accountList = MutableStateFlow<List<Account>>(emptyList())
    private var statusError = Color.Unspecified
    private var navigation = HSNavigation(NavBackStack<HSPage>(DesktopMainPage("test")))

    @BeforeTest
    fun setUp() {
        Locale.setDefault(Locale.ENGLISH)
        DesktopTestGraph.startUnlocked()
        loadKoinModules(
            module {
                single<IAccountManager> {
                    mockk(relaxed = true) {
                        every { activeAccountStateFlow } returns activeAccountState
                        every { accounts } answers { accountList.value }
                        every { accountsFlow } returns accountList
                        every { newAccountBackupRequiredFlow } returns MutableStateFlow(null)
                    }
                }
            }
        )
    }

    @AfterTest
    fun tearDown() {
        Locale.setDefault(systemLocale)
        DesktopTestGraph.stop()
    }

    @Test
    fun balanceTab_noAccount_offersToCreateOrImportAWallet() = runDesktopComposeUiTest {
        showApp()
        onNodeWithText(NEW_WALLET).assertDoesNotExist()

        runOnIdle { activeAccountState.value = ActiveAccountState.ActiveAccount(null) }
        onNodeWithText(NEW_WALLET).assertExists()

        runOnIdle { activeAccountState.value = ActiveAccountState.ActiveAccount(mnemonicAccount(isBackedUp = true)) }
        onNodeWithText(NEW_WALLET).assertDoesNotExist()
    }

    @Test
    fun settings_manageWallets_opensTheWalletList() = runDesktopComposeUiTest {
        showApp()
        onNodeWithContentDescription(SETTINGS).performClick()

        onNodeWithText(MANAGE_WALLETS).performScrollTo().performClick()

        val page = assertIs<ManageAccountsPage>(navigation.lastOrNull())
        assertEquals(ManageAccountsModule.Mode.Manage, page.input)
    }

    @Test
    fun settings_backupManager_opensTheBackupManager() = runDesktopComposeUiTest {
        showApp()
        onNodeWithContentDescription(SETTINGS).performClick()

        onNodeWithText(BACKUP_MANAGER).performScrollTo().performClick()

        assertIs<BackupManagerPage>(navigation.lastOrNull())
    }

    @Test
    fun settings_aboutPremium_opensAboutPremium() = runDesktopComposeUiTest {
        showApp()
        onNodeWithContentDescription(SETTINGS).performClick()

        onNodeWithText(ABOUT_PREMIUM).performScrollTo().performClick()

        assertIs<AboutPremiumPage>(navigation.lastOrNull())
    }

    @Test
    fun settings_appearance_opensAppearance() = runDesktopComposeUiTest {
        showApp()
        onNodeWithContentDescription(SETTINGS).performClick()

        onNodeWithText(APPEARANCE).performScrollTo().performClick()

        assertIs<AppearancePage>(navigation.lastOrNull())
    }

    @Test
    fun settings_blockchainSettings_opensBlockchainSettings() = runDesktopComposeUiTest {
        showApp()
        onNodeWithContentDescription(SETTINGS).performClick()

        onNodeWithText(BLOCKCHAIN_SETTINGS).performScrollTo().performClick()

        assertIs<BlockchainSettingsPage>(navigation.lastOrNull())
    }

    @Test
    fun marketsTab_disabled_isHiddenFromTheNavigation() = runDesktopComposeUiTest {
        appearanceStore().marketsTabEnabled = false
        showApp()

        onNodeWithContentDescription(MARKETS).assertDoesNotExist()
        onNodeWithContentDescription(SETTINGS).assertExists()

        runOnIdle { appearanceStore().marketsTabEnabled = true }
        onNodeWithContentDescription(MARKETS).assertExists()
    }

    @Test
    fun launchTab_marketLaunchPage_opensTheMarketsTab() = runDesktopComposeUiTest {
        appearanceStore().launchPage = LaunchPage.Market
        showApp()

        onNodeWithContentDescription(MARKETS).assertIsSelected()
    }

    @Test
    fun launchTab_autoAfterMarketThenSettings_reopensMarkets() {
        runDesktopComposeUiTest {
            showApp()
            onNodeWithContentDescription(MARKETS).performClick()
            onNodeWithContentDescription(SETTINGS).performClick()
        }

        runDesktopComposeUiTest {
            navigation = HSNavigation(NavBackStack<HSPage>(DesktopMainPage("test")))
            showApp()

            onNodeWithContentDescription(MARKETS).assertIsSelected()
        }
    }

    @Test
    fun settings_accountWithoutBackup_flagsManageWallets() = runDesktopComposeUiTest {
        showApp()
        onNodeWithContentDescription(SETTINGS).performClick()
        assertFalse(onNodeWithText(MANAGE_WALLETS).performScrollTo().shows(statusError))

        runOnIdle { accountList.value = listOf(mnemonicAccount(isBackedUp = false)) }

        assertTrue(onNodeWithText(MANAGE_WALLETS).shows(statusError))
    }

    private fun appearanceStore() = GlobalContext.get().get<AppearanceSettingsStore>()

    private fun ComposeUiTest.showApp() = setContent {
        TestWindowHost {
            ComposeAppTheme {
                statusError = ComposeAppTheme.colors.statusError
                PcashApp(remember { navigation })
            }
        }
    }

    private fun SemanticsNodeInteraction.shows(color: Color): Boolean {
        val pixels = captureToImage().toPixelMap()
        return (0 until pixels.width).any { x -> (0 until pixels.height).any { y -> pixels[x, y] == color } }
    }

    private companion object {
        const val NEW_WALLET = "New Wallet"
        const val SETTINGS = "Settings"
        const val MARKETS = "Markets"
        const val APPEARANCE = "Appearance"
        const val BLOCKCHAIN_SETTINGS = "Blockchain Settings"
        const val MANAGE_WALLETS = "Manage Wallets"
        const val BACKUP_MANAGER = "Backup Manager"
        const val ABOUT_PREMIUM = "About Premium"
    }
}
