package cash.p.terminal.modules.settings.main

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.runtime.NavBackStack
import androidx.test.core.app.ApplicationProvider
import cash.p.terminal.R
import cash.p.terminal.feature.miniapp.ui.miniapp.MiniAppPage
import cash.p.terminal.modules.backuplocal.fullbackup.BackupManagerPage
import cash.p.terminal.modules.basecurrency.BaseCurrencySettingsPage
import cash.p.terminal.modules.blockchainsettings.BlockchainSettingsPage
import cash.p.terminal.modules.main.PlainTestPage
import cash.p.terminal.modules.manageaccount.dialogs.BackupRequiredSheet
import cash.p.terminal.modules.multiswap.providersettings.SwapProvidersSettingsPage
import cash.p.terminal.modules.premium.settings.PremiumSettingsPage
import cash.p.terminal.modules.settings.about.AboutPage
import cash.p.terminal.modules.settings.about.ContactOptionsSheet
import cash.p.terminal.modules.settings.about.ContactUsPage
import cash.p.terminal.modules.settings.addresschecker.AddressCheckerPage
import cash.p.terminal.modules.settings.advancedsecurity.AdvancedSecurityPage
import cash.p.terminal.modules.settings.appearance.AppearancePage
import cash.p.terminal.modules.settings.donate.DonateTokenSelectPage
import cash.p.terminal.modules.settings.language.LanguageSettingsPage
import cash.p.terminal.modules.settings.security.SecuritySettingsPage
import cash.p.terminal.modules.softwareupdate.SoftwareUpdatePage
import cash.p.terminal.modules.walletconnect.AccountTypeNotSupportedSheet
import cash.p.terminal.modules.walletconnect.WCErrorNoAccountSheet
import cash.p.terminal.modules.walletconnect.WCManager
import cash.p.terminal.modules.walletconnect.list.WCListPage
import cash.p.terminal.navigation.AppPages
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.LocalHostLifecycleOwner
import cash.p.terminal.navigation.NavigationType
import cash.p.terminal.navigation.QrScannerInput
import cash.p.terminal.shared.settings.SettingsAction
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.horizontalsystems.core.IPinComponent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.reflect.KClass

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SettingsActionHandlerTest {

    @get:Rule
    val compose = createComposeRule()

    private val navigation = HSNavigation(NavBackStack<HSPage>(PlainTestPage()))
    private val scannerPage = PlainTestPage()
    private val appPages = mockk<AppPages> { every { qrScanner(any()) } returns scannerPage }
    private val context = mockk<Context>(relaxed = true)
    private var walletConnectSupport: WCManager.SupportState = WCManager.SupportState.Supported
    private var tonConnectSupported = true
    private var importTransactionFileCalls = 0
    private val viewModel = mockk<MainSettingsViewModel>(relaxed = true) {
        every { uiState } returns settingsContentTestState
        every { appVersion } returns "1.2.3"
        every { companyWebPage } returns "https://example.com"
        every { walletConnectSupportState } answers { walletConnectSupport }
        every { currentAccountSupportsTonConnect } answers { tonConnectSupported }
    }

    @Before
    fun setUp() {
        startKoin {
            modules(
                module {
                    single { mockk<IPinComponent>(relaxed = true) }
                    single { appPages }
                }
            )
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun handleSettingsAction_importTransactionFile_opensFilePicker() {
        handle(SettingsAction.ImportTransactionFile)

        assertEquals(1, importTransactionFileCalls)
        assertTrue(openedPages().isEmpty())
    }

    @Test
    fun settingsScreen_supportChangesBeforeClick_usesLatestConnectionSupport() {
        compose.setContent {
            CompositionLocalProvider(LocalHostLifecycleOwner provides LocalLifecycleOwner.current) {
                ComposeAppTheme {
                    SettingsScreen(navigation, PaddingValues(), viewModel)
                }
            }
        }

        walletConnectSupport = WCManager.SupportState.NotSupportedDueToNoActiveAccount
        tonConnectSupported = false
        compose.onNodeWithText("WalletConnect").performScrollTo().performClick()
        val application = ApplicationProvider.getApplicationContext<Application>()
        compose.onNodeWithText(application.getString(R.string.Settings_TonConnect))
            .performScrollTo()
            .performClick()

        assertEquals(
            listOf(WCErrorNoAccountSheet::class, AccountTypeNotSupportedSheet::class),
            openedPages().map { it::class },
        )
    }

    @Test
    fun handleSettingsAction_walletConnectSupportStates_navigatesToMatchingDestinations() {
        val account = Account(
            id = "account-id",
            name = "Wallet",
            type = AccountType.EvmAddress("0x1"),
            origin = AccountOrigin.Created,
            level = 0,
        )

        handle(SettingsAction.WalletConnect)
        walletConnectSupport = WCManager.SupportState.NotSupportedDueToNoActiveAccount
        handle(SettingsAction.WalletConnect)
        walletConnectSupport = WCManager.SupportState.NotSupportedDueToNonBackedUpAccount(account)
        handle(SettingsAction.WalletConnect)
        walletConnectSupport = WCManager.SupportState.NotSupported
        handle(SettingsAction.WalletConnect)

        val pages = openedPages()
        assertEquals(
            listOf(
                WCListPage::class,
                WCErrorNoAccountSheet::class,
                BackupRequiredSheet::class,
                AccountTypeNotSupportedSheet::class,
            ),
            pages.map { it::class },
        )
        assertNull((pages[0] as WCListPage).input)
        assertEquals(account, (pages[2] as BackupRequiredSheet).input.account)
    }

    @Test
    fun handleSettingsAction_offlineBroadcast_opensQrScannerWithTitleAndPaste() {
        handle(SettingsAction.OfflineBroadcast)

        verify { appPages.qrScanner(QrScannerInput("Raw transaction", showPasteButton = true)) }
        assertSame(scannerPage, openedPages().single())
        assertEquals(NavigationType.SlideFromBottom, scannerPage.navType)
    }

    @Test
    fun handleSettingsAction_simpleActions_opensDestinationsWithRightSlide() {
        val destinations: List<Pair<SettingsAction, KClass<out HSPage>>> = listOf(
            SettingsAction.Donate to DonateTokenSelectPage::class,
            SettingsAction.MiniApp to MiniAppPage::class,
            SettingsAction.BlockchainSettings to BlockchainSettingsPage::class,
            SettingsAction.BackupManager to BackupManagerPage::class,
            SettingsAction.SecurityCenter to SecuritySettingsPage::class,
            SettingsAction.Appearance to AppearancePage::class,
            SettingsAction.BaseCurrency to BaseCurrencySettingsPage::class,
            SettingsAction.Language to LanguageSettingsPage::class,
            SettingsAction.AddressChecker to AddressCheckerPage::class,
            SettingsAction.SwapProviders to SwapProvidersSettingsPage::class,
            SettingsAction.PremiumSettings to PremiumSettingsPage::class,
            SettingsAction.AdvancedSecurity to AdvancedSecurityPage::class,
            SettingsAction.SoftwareUpdate to SoftwareUpdatePage::class,
            SettingsAction.AboutApp to AboutPage::class,
        )

        destinations.forEach { (action, _) -> handle(action) }

        val pages = openedPages()
        assertEquals(destinations.map { it.second }, pages.map { it::class })
        assertTrue(pages.all { it.navType == NavigationType.SlideFromRight })
    }

    @Test
    fun handleSettingsAction_contactPayCoreState_opensMatchingDestination() {
        handle(SettingsAction.Contact(true))
        handle(SettingsAction.Contact(false))

        val pages = openedPages()
        assertEquals(listOf(ContactUsPage::class, ContactOptionsSheet::class), pages.map { it::class })
        assertNull((pages[1] as ContactOptionsSheet).input)
    }

    private fun openedPages(): List<HSPage> = navigation.backStack.drop(1)

    private fun handle(action: SettingsAction) {
        handleSettingsAction(
            action,
            navigation,
            appPages,
            viewModel,
            context,
            rawTxScanTitle = "Raw transaction",
            walletConnectTitle = "WalletConnect",
            tonConnectTitle = "TON Connect",
            importTransactionFile = { importTransactionFileCalls++ },
        )
    }
}
