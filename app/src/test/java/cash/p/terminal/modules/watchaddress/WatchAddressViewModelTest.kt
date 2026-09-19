package cash.p.terminal.modules.watchaddress

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.adapters.zcash.ZcashAddressDeriver
import cash.p.terminal.core.managers.ZcashBirthdayProvider
import cash.p.terminal.entities.Address
import cash.p.terminal.modules.address.AddressParserChain
import cash.p.terminal.modules.address.IAddressHandler
import cash.p.terminal.modules.enablecoin.restoresettings.TokenConfig
import cash.p.terminal.ui_compose.entities.DataState
import cash.p.terminal.wallet.AccountType
import cash.p.zcash.Addresses
import cash.p.zcash.ZcashSdk
import cash.p.zcash.isValidKey
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.hdwalletkit.Curve
import io.horizontalsystems.hdwalletkit.HDExtendedKey
import io.horizontalsystems.hdwalletkit.HDExtendedKeyVersion
import io.horizontalsystems.hdwalletkit.HDKeychain
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Test
import org.junit.After
import org.junit.Before
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WatchAddressViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val zcashAddressDeriver = mockk<ZcashAddressDeriver>()
    private val addressParserChain = mockk<AddressParserChain> {
        every { supportedHandler(any()) } returns null
    }
    private val watchAddressService = mockk<WatchAddressService> {
        every { nextWatchAccountName() } returns "Watch 1"
        every { watchAll(any(), any(), any()) } returns Unit
    }
    private val zcashBirthdayProvider = mockk<ZcashBirthdayProvider> {
        every { getLatestCheckpointBlockHeight() } returns CHECKPOINT
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        startKoin {
            modules(
                module {
                    single { zcashAddressDeriver }
                    single { zcashBirthdayProvider }
                    single<DispatcherProvider> { TestDispatcherProvider(dispatcher, TestScope(dispatcher)) }
                }
            )
        }
        mockkStatic("cash.p.zcash.ZcashSdkKt")
        every { ZcashSdk.isValidKey(any(), any()) } returns true
        coEvery { zcashAddressDeriver.addresses(any()) } returns addresses
    }

    @After
    fun tearDown() {
        stopKoin()
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun onEnterInput_saplingViewingKey_watchesAsSaplingAccount() = runTest(dispatcher) {
        val viewModel = enterInput(SAPLING_VIEWING_KEY)

        assertEquals(DataState.Success(SAPLING_VIEWING_KEY), viewModel.uiState.inputState)
        assertEquals(SubmitButtonType.Watch(true), viewModel.uiState.submitButtonType)

        viewModel.onClickNext()
        advanceUntilIdle()

        assertEquals(AccountType.ZCashSaplingKey(SAPLING_VIEWING_KEY), viewModel.uiState.accountType)
    }

    @Test
    fun onEnterInput_unifiedViewingKey_watchesAsUfvkAccount() = runTest(dispatcher) {
        val viewModel = enterInput(UFVK)

        assertEquals(SubmitButtonType.Watch(true), viewModel.uiState.submitButtonType)

        viewModel.onClickNext()
        advanceUntilIdle()

        assertEquals(AccountType.ZCashUfvKey(UFVK), viewModel.uiState.accountType)
    }

    @Test
    fun onEnterInput_saplingSpendingKey_reportsPrivateKeyNotWatchable() = runTest(dispatcher) {
        val viewModel = enterInput(SAPLING_SPENDING_KEY)

        assertSame(PrivateKeyNotWatchable, errorOf(viewModel))
        assertEquals(SubmitButtonType.Watch(false), viewModel.uiState.submitButtonType)
    }

    @Test
    fun onEnterInput_viewingKeyTheSdkCannotDerive_reportsUnsupportedAddress() = runTest(dispatcher) {
        coEvery { zcashAddressDeriver.addresses(any()) } throws IllegalArgumentException("bad key")

        val viewModel = enterInput(SAPLING_VIEWING_KEY)

        assertSame(UnsupportedAddress, errorOf(viewModel))
        assertEquals(SubmitButtonType.Watch(false), viewModel.uiState.submitButtonType)
    }

    @Test
    fun onEnterInput_transparentXpub_stillWatchesAsExtendedKey() = runTest(dispatcher) {
        val viewModel = enterInput(accountXpub)

        assertEquals(SubmitButtonType.Next(true), viewModel.uiState.submitButtonType)

        viewModel.onClickNext()
        advanceUntilIdle()

        assertEquals(AccountType.HdExtendedKey(accountXpub), viewModel.uiState.accountType)
    }

    @Test
    fun onEnterInput_transparentXprv_reportsPrivateKeyNotWatchable() = runTest(dispatcher) {
        val viewModel = enterInput(accountXprv)

        assertSame(PrivateKeyNotWatchable, errorOf(viewModel))
    }

    @Test
    fun onClickWatch_zcashViewingKey_asksForBirthdayHeightAndCreatesNothing() = runTest(dispatcher) {
        val viewModel = enterInput(UFVK)

        viewModel.onClickWatch()

        assertTrue(viewModel.uiState.zcashHeightRequested)
        assertFalse(viewModel.uiState.accountCreated)
        verify(exactly = 0) { watchAddressService.watchAll(any(), any(), any()) }
    }

    @Test
    fun onZcashHeightEntered_cancelled_keepsTheEnteredKeyAndCreatesNothing() = runTest(dispatcher) {
        val viewModel = requestZcashHeight(UFVK)

        viewModel.onZcashHeightEntered(null)

        assertEquals(UFVK, viewModel.enteredInput)
        assertEquals(SubmitButtonType.Watch(true), viewModel.uiState.submitButtonType)
        assertFalse(viewModel.uiState.accountCreated)
        verify(exactly = 0) { watchAddressService.watchAll(any(), any(), any()) }
    }

    @Test
    fun onZcashHeightEntered_existingWalletHeight_watchesFromThatHeight() = runTest(dispatcher) {
        val viewModel = requestZcashHeight(UFVK)

        viewModel.onZcashHeightEntered(TokenConfig(birthdayHeight = "3100000", restoreAsNew = false))

        assertTrue(viewModel.uiState.accountCreated)
        verify {
            watchAddressService.watchAll(AccountType.ZCashUfvKey(UFVK), "Watch 1", 3_100_000L)
        }
    }

    @Test
    fun onZcashHeightEntered_newWalletWithoutHeight_watchesFromTheLatestCheckpoint() = runTest(dispatcher) {
        val viewModel = requestZcashHeight(SAPLING_VIEWING_KEY)

        viewModel.onZcashHeightEntered(TokenConfig(birthdayHeight = null, restoreAsNew = true))

        verify {
            watchAddressService.watchAll(
                AccountType.ZCashSaplingKey(SAPLING_VIEWING_KEY),
                "Watch 1",
                CHECKPOINT
            )
        }
    }

    @Test
    fun onClickWatch_nonZcashAddress_watchesWithoutAskingForHeight() = runTest(dispatcher) {
        every { addressParserChain.supportedHandler(any()) } returns mockk<IAddressHandler> {
            every { parseAddress(any()) } returns Address(
                hex = TRON_ADDRESS,
                blockchainType = BlockchainType.Tron
            )
        }

        val viewModel = enterInput(TRON_ADDRESS)
        viewModel.onClickWatch()

        assertFalse(viewModel.uiState.zcashHeightRequested)
        assertTrue(viewModel.uiState.accountCreated)
        verify { watchAddressService.watchAll(AccountType.TronAddress(TRON_ADDRESS), "Watch 1", null) }
    }

    private fun kotlinx.coroutines.test.TestScope.requestZcashHeight(key: String): WatchAddressViewModel {
        val viewModel = enterInput(key)
        viewModel.onClickWatch()
        viewModel.zcashHeightRequestOpened()
        return viewModel
    }

    private fun kotlinx.coroutines.test.TestScope.enterInput(input: String): WatchAddressViewModel {
        val viewModel = WatchAddressViewModel(watchAddressService, addressParserChain)
        viewModel.onEnterInput(input)
        advanceUntilIdle()
        return viewModel
    }

    private fun errorOf(viewModel: WatchAddressViewModel) =
        (viewModel.uiState.inputState as DataState.Error).error

    private companion object {
        const val SAPLING_VIEWING_KEY = "zxviews1qsaplingviewingkey"
        const val SAPLING_SPENDING_KEY = "secret-extended-key-main1qsaplingspendingkey"
        const val UFVK = "uview1qunifiedviewingkey"
        const val TRON_ADDRESS = "TWatchOnlyTronAddress"
        const val CHECKPOINT = 3_424_810L

        val addresses = Addresses(
            unified = "u1unified",
            sapling = "zs1sapling",
            orchard = "o1orchard",
            transparent = "t1transparent",
            diversifierIndex = 0,
        )

        private val accountKey = HDKeychain(ByteArray(64) { (it + 1).toByte() }, Curve.Secp256K1)
            .getKeyByPath("m/44'/0'/0'")

        val accountXprv = HDExtendedKey(accountKey, HDExtendedKeyVersion.xprv).serializePrivate()
        val accountXpub = HDExtendedKey(accountKey, HDExtendedKeyVersion.xprv).serializePublic()
    }
}
