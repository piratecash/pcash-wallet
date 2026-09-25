package cash.p.terminal.modules.main

import android.net.Uri
import cash.p.terminal.R
import cash.p.terminal.core.IBackupManager
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.IRateAppManager
import cash.p.terminal.core.ITermsManager
import cash.p.terminal.core.deeplink.DeeplinkParser
import cash.p.terminal.core.managers.ReleaseNotesManager
import cash.p.terminal.feature.logging.domain.usecase.LogLoginAttemptUseCase
import cash.p.terminal.modules.coin.CoinPage
import cash.p.terminal.modules.market.platform.MarketPlatformPage
import cash.p.terminal.modules.market.topplatforms.Platform
import cash.p.terminal.modules.nft.collection.NftCollectionPage
import cash.p.terminal.modules.softwareupdate.AppUpdateChecker
import cash.p.terminal.modules.walletconnect.WCManager
import cash.p.terminal.modules.walletconnect.WCSessionManager
import cash.p.terminal.modules.walletconnect.list.WCListPage
import cash.p.terminal.premium.domain.usecase.CheckPremiumUseCase
import cash.p.terminal.premium.domain.usecase.PremiumType
import cash.p.terminal.shared.main.MainDestination
import cash.p.terminal.strings.helpers.Translator
import cash.p.terminal.ui_compose.CoinFragmentInput
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.IAccountManager
import io.horizontalsystems.core.IPinComponent
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.reactivex.Flowable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class MainViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private val pinComponent = mockk<IPinComponent>(relaxed = true)
    private val rateAppManager = mockk<IRateAppManager>(relaxed = true)
    private val backupManager = mockk<IBackupManager>(relaxed = true)
    private val termsManager = mockk<ITermsManager>(relaxed = true)
    private val accountManager = mockk<IAccountManager>(relaxed = true)
    private val releaseNotesManager = mockk<ReleaseNotesManager>(relaxed = true)
    private val localStorage = mockk<ILocalStorage>(relaxed = true)
    private val wcSessionManager = mockk<WCSessionManager>(relaxed = true)
    private val wcManager = mockk<WCManager>(relaxed = true)
    private val logLoginAttemptUseCase = mockk<LogLoginAttemptUseCase>(relaxed = true)
    private val deeplinkParser = mockk<DeeplinkParser>(relaxed = true)
    private val appUpdateChecker = mockk<AppUpdateChecker>(relaxed = true)
    private val checkPremiumUseCase = mockk<CheckPremiumUseCase>(relaxed = true)

    private var currentAccounts: List<Account> = emptyList()
    private var accountsEmpty = false
    private var allTermsAccepted = true
    private var storedMainTab: MainDestination? = null
    private val premiumTypesFlow = MutableStateFlow<Map<String, PremiumType>>(emptyMap())
    private val marketsTabEnabledFlow = MutableStateFlow(true)

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)

        every { pinComponent.isLockedFlow } returns MutableStateFlow(false)
        every { pinComponent.pinSetFlowable } returns Flowable.empty()
        every { pinComponent.isPinSet } returns true
        every { localStorage.marketsTabEnabledFlow } returns marketsTabEnabledFlow
        every { localStorage.mainTab } answers { storedMainTab }
        every { localStorage.mainTab = any() } answers { storedMainTab = firstArg() }
        every { localStorage.isSystemPinRequired } returns true
        every { termsManager.termsAcceptedSignalFlow } returns emptyFlow()
        every { termsManager.allTermsAccepted } answers { allTermsAccepted }
        every { rateAppManager.showRateAppFlow } returns emptyFlow()
        every { backupManager.allBackedUpFlow } returns emptyFlow()
        every { backupManager.allBackedUp } returns true
        every { wcSessionManager.pendingRequestCountFlow } returns MutableStateFlow(0)
        every { accountManager.accountsFlow } returns emptyFlow()
        every { accountManager.activeAccountStateFlow } returns emptyFlow()
        every { accountManager.hasNonStandardAccount } returns false
        every { accountManager.isAccountsEmpty } answers { accountsEmpty }
        every { accountManager.accounts } answers { currentAccounts }
        coEvery { logLoginAttemptUseCase.selfieEnabledAndHasProblem() } returns false
        every { appUpdateChecker.updateAvailable } returns MutableStateFlow(false)
        every { checkPremiumUseCase.premiumTypesFlow } returns premiumTypesFlow
        every { deeplinkParser.parse(any<Uri>()) } returns null
        mockkObject(Translator)
        every { Translator.getString(R.string.DeeplinkScheme) } returns "pcash"

        startKoin {
            modules(
                module {
                    single { appUpdateChecker }
                    single { checkPremiumUseCase }
                }
            )
        }
    }

    @After
    fun tearDown() {
        stopKoin()
        unmockkObject(Translator)
        Dispatchers.resetMain()
    }

    @Test
    fun premiumTypesFlow_seedsWalletSwitchTypes() = runTest(dispatcher) {
        premiumTypesFlow.value = mapOf("a" to PremiumType.PIRATE)

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(mapOf("a" to PremiumType.PIRATE), viewModel.uiState.walletSwitchPremiumTypes)
    }

    @Test
    fun premiumTypesFlow_updatesWalletSwitchTypesOnEmission() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertEquals(emptyMap(), viewModel.uiState.walletSwitchPremiumTypes)

        // Background re-scan completes and the flow emits: the sheet's badge map updates.
        premiumTypesFlow.value = mapOf("b" to PremiumType.COSA)
        advanceUntilIdle()

        assertEquals(mapOf("b" to PremiumType.COSA), viewModel.uiState.walletSwitchPremiumTypes)
    }

    @Test
    fun navigationItems_marketsEnabledAndDisabled_updatesOrder() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(MainDestination.entries.toList(), viewModel.uiState.mainNavItems.map { it.mainNavItem })

        marketsTabEnabledFlow.value = false
        advanceUntilIdle()

        assertEquals(
            listOf(MainDestination.Balance, MainDestination.Transactions, MainDestination.Settings),
            viewModel.uiState.mainNavItems.map { it.mainNavItem },
        )
    }

    @Test
    fun navigationItems_accountsEmpty_disablesTransactions() = runTest(dispatcher) {
        accountsEmpty = true
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(
            false,
            viewModel.uiState.mainNavItems.first { it.mainNavItem == MainDestination.Transactions }.enabled
        )
    }

    @Test
    fun navigationItems_termsNotAccepted_showsSettingsBadge() = runTest(dispatcher) {
        allTermsAccepted = false
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(
            MainModule.BadgeType.BadgeDot,
            viewModel.uiState.mainNavItems.first { it.mainNavItem == MainDestination.Settings }.badge,
        )
    }

    @Test
    fun onSelect_destinationPersistsSelection() = runTest(dispatcher) {
        val viewModel = createViewModel()
        viewModel.onSelect(MainDestination.Market)
        advanceUntilIdle()

        assertEquals(MainDestination.Market, storedMainTab)
        assertEquals(MainDestination.Market, viewModel.uiState.mainNavItems.first { it.selected }.mainNavItem)
    }

    @Test
    fun handleDeepLink_coinPage_opensCoinPageFromRight() = runTest(dispatcher) {
        val deeplinkPage = resolveDeepLink("pcash://coin-page?uid=bitcoin")

        assertEquals(false, deeplinkPage?.fromBottom)
        assertEquals("bitcoin", assertIs<CoinPage>(deeplinkPage?.page).input.coinUid)
    }

    @Test
    fun handleDeepLink_nftCollection_opensNftCollectionPageFromRight() = runTest(dispatcher) {
        val deeplinkPage = resolveDeepLink("pcash://nft-collection?uid=punks&blockchainTypeUid=ethereum")

        assertEquals(false, deeplinkPage?.fromBottom)
        assertEquals(
            NftCollectionPage.Input("punks", "ethereum"),
            assertIs<NftCollectionPage>(deeplinkPage?.page).input,
        )
    }

    @Test
    fun handleDeepLink_topPlatforms_opensMarketPlatformPageFromRight() = runTest(dispatcher) {
        val deeplinkPage = resolveDeepLink("pcash://top-platforms?uid=ethereum&title=Ethereum")

        assertEquals(false, deeplinkPage?.fromBottom)
        assertEquals(Platform("ethereum", "Ethereum"), assertIs<MarketPlatformPage>(deeplinkPage?.page).input)
    }

    @Test
    fun handleDeepLink_walletConnectSupported_opensWcListPageFromRight() = runTest(dispatcher) {
        every { wcManager.getWalletConnectSupportState() } returns WCManager.SupportState.Supported
        val link = "wc:topic@2?relay-protocol=irn&symKey=key"

        val deeplinkPage = resolveDeepLink(link)

        assertEquals(false, deeplinkPage?.fromBottom)
        assertEquals(WCListPage.Input(link), assertIs<WCListPage>(deeplinkPage?.page).input)
    }

    @Test
    fun handleDeepLink_parserRecognizesLink_passesParsedPageThrough() = runTest(dispatcher) {
        val parsed = DeeplinkPage(CoinPage(CoinFragmentInput("any")), fromBottom = true)
        every { deeplinkParser.parse(any<Uri>()) } returns parsed

        assertSame(parsed, resolveDeepLink("pcash://auth?token=jwt"))
    }

    private fun resolveDeepLink(link: String): DeeplinkPage? {
        val viewModel = createViewModel()
        viewModel.handleDeepLink(Uri.parse(link))
        return viewModel.uiState.deeplinkPage
    }

    private fun createViewModel() = MainViewModel(
        pinComponent = pinComponent,
        rateAppManager = rateAppManager,
        backupManager = backupManager,
        termsManager = termsManager,
        accountManager = accountManager,
        releaseNotesManager = releaseNotesManager,
        localStorage = localStorage,
        wcSessionManager = wcSessionManager,
        wcManager = wcManager,
        logLoginAttemptUseCase = logLoginAttemptUseCase,
        deeplinkParser = deeplinkParser
    )
}
