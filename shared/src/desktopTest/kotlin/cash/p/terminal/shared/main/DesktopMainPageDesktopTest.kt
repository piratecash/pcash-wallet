package cash.p.terminal.shared.main

import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.navigation3.runtime.NavBackStack
import cash.p.terminal.core.AppearanceSettingsStore
import cash.p.terminal.core.IClipboardManager
import cash.p.terminal.core.managers.AppVersionManager
import cash.p.terminal.core.managers.ReleaseNotesManager
import cash.p.terminal.entities.LaunchPage
import cash.p.terminal.modules.backuplocal.fullbackup.BackupManagerPage
import cash.p.terminal.modules.blockchainsettings.BlockchainSettingsPage
import cash.p.terminal.modules.manageaccounts.ManageAccountsModule
import cash.p.terminal.modules.manageaccounts.ManageAccountsPage
import cash.p.terminal.modules.premium.about.AboutPremiumPage
import cash.p.terminal.modules.releasenotes.ReleaseNotesPage
import cash.p.terminal.modules.settings.about.AboutPage
import cash.p.terminal.modules.settings.about.ContactOptionsSheet
import cash.p.terminal.modules.settings.appearance.AppearancePage
import cash.p.terminal.modules.settings.appstatus.AppStatusPage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.shared.DesktopTestGraph
import cash.p.terminal.shared.PcashApp
import cash.p.terminal.shared.TestWindowHost
import cash.p.terminal.shared.desktop.session.mnemonicAccount
import cash.p.terminal.ui_compose.components.UrlOpener
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.ActiveAccountState
import cash.p.terminal.wallet.IAccountManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class DesktopMainPageDesktopTest {

    private val systemLocale: Locale = Locale.getDefault()
    private val activeAccountState = MutableStateFlow<ActiveAccountState>(ActiveAccountState.NotLoaded)
    private val accountList = MutableStateFlow<List<Account>>(emptyList())
    private var statusError = Color.Unspecified
    private var navigation = HSNavigation(NavBackStack<HSPage>(DesktopMainPage()))

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
    fun settings_aboutApp_opensAbout() = runDesktopComposeUiTest {
        showApp()
        onNodeWithContentDescription(SETTINGS).performClick()

        onNodeWithText(ABOUT_APP).performScrollTo().performClick()

        assertIs<AboutPage>(navigation.lastOrNull())
    }

    @Test
    fun settings_contact_opensTheContactSheetWithDefaults() = runDesktopComposeUiTest {
        showApp()
        onNodeWithContentDescription(SETTINGS).performClick()

        onNodeWithText(CONTACT).performScrollTo().performClick()

        val sheet = assertIs<ContactOptionsSheet>(navigation.lastOrNull())
        assertNull(sheet.input)
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
    fun settings_desktopOnlyUnsupportedRows_areNotShown() = runDesktopComposeUiTest {
        showApp()
        onNodeWithContentDescription(SETTINGS).performClick()

        listOf("Mini App", "WalletConnect", "Ton Connect", "Software Updates", "Advanced Security", "Rate Us")
            .forEach { onNodeWithText(it).assertDoesNotExist() }
        onNodeWithText("About App").performScrollTo().assertExists()
    }

    @Test
    fun settings_shareApp_copiesTheShareTextToTheClipboard() = runDesktopComposeUiTest {
        val clipboard = mockk<IClipboardManager>(relaxed = true)
        loadKoinModules(module { single<IClipboardManager> { clipboard } })
        showApp()
        onNodeWithContentDescription(SETTINGS).performClick()

        onNodeWithText("Tell Friends").performScrollTo().performClick()

        verify(exactly = 1) { clipboard.copyText("Be Free\nhttps://p.cash/#MOBILE_APPS\n") }
    }

    @Test
    fun settings_companyLogo_opensTheCompanyWebsite() = runDesktopComposeUiTest {
        val urlOpener = mockk<UrlOpener>(relaxed = true)
        loadKoinModules(module { single<UrlOpener> { urlOpener } })
        showApp()
        onNodeWithContentDescription(SETTINGS).performClick()

        onAllNodes(hasClickAction()).onLast().performScrollTo().performClick()

        verify(exactly = 1) { urlOpener.open("https://p.cash/") }
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
            navigation = HSNavigation(NavBackStack<HSPage>(DesktopMainPage()))
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

    @Test
    fun mainPage_changelogDue_opensReleaseNotesOnce() = runDesktopComposeUiTest {
        val releaseNotesManager = mockk<ReleaseNotesManager> { every { shouldShowChangeLog() } returns true }
        loadKoinModules(module { single { releaseNotesManager } })
        showApp()
        waitForIdle()

        val page = assertIs<ReleaseNotesPage>(navigation.lastOrNull())
        assertTrue(page.input.showAsClosablePopup)
        assertEquals(1, navigation.backStack.count { it is ReleaseNotesPage })
        verify(exactly = 1) { releaseNotesManager.shouldShowChangeLog() }
    }

    @Test
    fun mainPage_changelogNotDue_staysOnTheMainPage() = runDesktopComposeUiTest {
        val releaseNotesManager = mockk<ReleaseNotesManager> { every { shouldShowChangeLog() } returns false }
        loadKoinModules(module { single { releaseNotesManager } })
        showApp()
        waitForIdle()

        assertIs<DesktopMainPage>(navigation.lastOrNull())
    }

    @Test
    fun mainPage_unlocked_storesTheAppVersionBeforeCheckingTheChangelog() = runDesktopComposeUiTest {
        val appVersionManager = mockk<AppVersionManager>(relaxed = true)
        val releaseNotesManager = mockk<ReleaseNotesManager> { every { shouldShowChangeLog() } returns false }
        loadKoinModules(module { single { appVersionManager }; single { releaseNotesManager } })
        showApp()
        waitForIdle()

        verifyOrder {
            appVersionManager.storeAppVersion()
            releaseNotesManager.shouldShowChangeLog()
        }
    }

    @Test
    fun appStatus_desktop_offersCopySaveToFileAndCacheButNoShare() = runDesktopComposeUiTest {
        showApp()
        runOnIdle { navigation.slideFromRight(AppStatusPage()) }

        waitUntil(timeoutMillis = 30_000) { onAllNodesWithText(COPY).fetchSemanticsNodes().isNotEmpty() }

        onNodeWithText(SAVE_TO_FILE).assertExists()
        onNodeWithText(SHARE).assertDoesNotExist()
        onNodeWithText(APP_CACHE).assertExists()
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
        const val ABOUT_APP = "About App"
        const val CONTACT = "Contact Us"
        const val COPY = "Copy"
        const val SHARE = "Share"
        const val SAVE_TO_FILE = "Save to file"
        const val APP_CACHE = "App Cache"
    }
}
