package cash.p.terminal.modules.balance

import cash.p.terminal.core.AppearanceSettingsStore
import cash.p.terminal.core.BalanceSettingsStorage
import cash.p.terminal.core.ICoinManager
import cash.p.terminal.core.managers.OfflineModeManager
import cash.p.terminal.core.managers.PriceManager
import cash.p.terminal.core.managers.SeedPhraseQrCrypto
import cash.p.terminal.core.storage.PendingMultiSwapStorage
import cash.p.terminal.modules.address.AddressHandlerFactory
import cash.p.terminal.modules.address.AddressParserChain
import cash.p.terminal.modules.address.IAddressHandler
import cash.p.terminal.modules.displayoptions.DisplayDiffOptionType
import cash.p.terminal.modules.displayoptions.DisplayPricePeriod
import cash.p.terminal.modules.settings.appearance.PriceChangeInterval
import cash.p.terminal.wallet.ActiveAccountState
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.balance.BalanceViewType
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import cash.p.terminal.wallet.useCases.WalletUseCase
import cash.p.terminal.core.TestDispatcherProvider
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class BalanceViewModelScanTest : KoinTest {

    private val scheduler = TestCoroutineScheduler()
    private val ioExecutor = Executors.newSingleThreadExecutor { Thread(it, IO_THREAD) }
    private var ioDispatcher: CoroutineDispatcher = ioExecutor.asCoroutineDispatcher()

    @get:Rule
    val koinRule = KoinTestRule.create {
        modules(
            module {
                single<IAccountManager> {
                    mockk(relaxed = true) {
                        every { activeAccountStateFlow } returns
                            MutableStateFlow<ActiveAccountState>(ActiveAccountState.NotLoaded)
                    }
                }
                single<IAdapterManager> { mockk(relaxed = true) }
                single<AppearanceSettingsStore> {
                    mockk(relaxed = true) { every { balanceTabButtonsEnabledFlow } returns MutableStateFlow(true) }
                }
                single<ICoinManager> { mockk(relaxed = true) }
                single<WalletUseCase> { mockk(relaxed = true) }
                single<SeedPhraseQrCrypto> { mockk(relaxed = true) }
                single<PendingMultiSwapStorage> {
                    mockk(relaxed = true) { every { observeForActiveAccount(any()) } returns flowOf(emptyList()) }
                }
                single<OfflineModeManager> {
                    mockk(relaxed = true) { every { effectiveFlow } returns MutableStateFlow(emptySet()) }
                }
                single<DispatcherProvider> {
                    TestDispatcherProvider(
                        dispatcher = Dispatchers.Unconfined,
                        applicationScope = CoroutineScope(Dispatchers.Unconfined),
                        io = ioDispatcher,
                    )
                }
            }
        )
    }

    private val detectorThreads = Collections.synchronizedList(mutableListOf<String>())
    private val detected = CountDownLatch(1)
    private val parserChain = mockk<AddressParserChain> {
        every { supportedAddressHandlers(any()) } answers {
            // Coroutine debug mode appends " @coroutine#N" to the thread name.
            detectorThreads += Thread.currentThread().name.substringBefore(" @")
            detected.countDown()
            emptyList()
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))
    }

    @After
    fun tearDown() {
        // The scan resumes on the IO thread after detection; let it finish before Main is reset.
        ioExecutor.shutdown()
        ioExecutor.awaitTermination(5, TimeUnit.SECONDS)
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun handleScannedData_plainAddress_detectsChainsOffCallerThread() {
        val viewModel = createViewModel()
        val callerThread = Thread.currentThread().name

        viewModel.handleScannedData(TRON_ADDRESS)

        assertTrue(
            "Chain detection ran on the caller (main) thread: $detectorThreads",
            callerThread !in detectorThreads,
        )
        assertTrue("Chain detection never ran", detected.await(5, TimeUnit.SECONDS))
        assertEquals(listOf(IO_THREAD), detectorThreads.toList())
    }

    @Test
    fun handleScannedData_newScanBeforePreviousResolved_previousResultDiscarded() {
        ioDispatcher = StandardTestDispatcher(scheduler)
        every { parserChain.supportedAddressHandlers(TRON_ADDRESS) } returns listOf(
            mockk<IAddressHandler> { every { blockchainType } returns BlockchainType.Tron }
        )
        val viewModel = createViewModel()

        viewModel.handleScannedData(TRON_ADDRESS)
        viewModel.handleScannedData(INVALID_QR)
        scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.openSend)
        assertNotNull(viewModel.uiState.errorMessage)
    }

    private fun createViewModel(): BalanceViewModel {
        val addressHandlerFactory = mockk<AddressHandlerFactory> {
            every { parserChain(null) } returns parserChain
        }
        return BalanceViewModel(
            service = mockk(relaxed = true) { every { balanceItemsFlow } returns MutableStateFlow(emptyList()) },
            balanceViewItemFactory = mockk(relaxed = true),
            balanceViewTypeManager = mockk {
                every { balanceViewTypeFlow } returns MutableStateFlow(BalanceViewType.entries.first())
            },
            totalBalance = mockk(relaxed = true),
            localStorage = mockk<BalanceSettingsStorage>(relaxed = true) {
                every { displayDiffOptionType } returns DisplayDiffOptionType.BOTH
                every { displayDiffPricePeriod } returns DisplayPricePeriod.ONE_DAY
            },
            platformActions = mockk(relaxed = true),
            addressHandlerFactory = addressHandlerFactory,
            priceManager = mockk<PriceManager>(relaxed = true) {
                every { priceChangeIntervalFlow } returns MutableStateFlow(PriceChangeInterval.entries.first())
                every { displayPricePeriodFlow } returns MutableStateFlow(DisplayPricePeriod.ONE_DAY)
                every { displayDiffOptionTypeFlow } returns MutableStateFlow(DisplayDiffOptionType.BOTH)
            },
            balanceHiddenManager = mockk<IBalanceHiddenManager>(relaxed = true) {
                every { anyWalletVisibilityChangedFlow } returns MutableSharedFlow()
            },
        )
    }

    private companion object {
        const val IO_THREAD = "scan-io"
        const val TRON_ADDRESS = "TJRabPrwbZy45sbavfcjinPJC18kjpRTv8"
        const val INVALID_QR = "not an address"
    }
}
