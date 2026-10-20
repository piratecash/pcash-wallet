package cash.p.terminal.modules.manageaccount.zcashkeys

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.adapters.zcash.ZcashKeyExporter
import cash.p.terminal.core.adapters.zcash.ZcashPrivateKeyType
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.zcash.ZcashSdk
import cash.p.zcash.deriveSaplingViewingKey
import cash.p.zcash.deriveSpendingKey
import cash.p.zcash.deriveTransparentAccountKey
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkStatic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigInteger

@OptIn(ExperimentalCoroutinesApi::class)
class ZcashKeysViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val accountManager = mockk<IAccountManager>()
    private val zcashKeyExporter =
        spyk(ZcashKeyExporter(TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher))))

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_mnemonicAccount_listsBothKeyTypes() {
        val viewModel = createViewModel()

        assertEquals(ZcashPrivateKeyType.entries, viewModel.uiState.available)
    }

    @Test
    fun init_standaloneKeyAccount_listsNoKeys() {
        val viewModel = createViewModel(AccountType.EvmPrivateKey(BigInteger.ONE))

        assertTrue(viewModel.uiState.available.isEmpty())
    }

    @Test
    fun init_nullAccount_closesScreen() {
        every { accountManager.account(ACCOUNT_ID) } returns null

        val viewModel = ZcashKeysViewModel(ACCOUNT_ID, accountManager, zcashKeyExporter)

        assertTrue(viewModel.uiState.closeScreen)
    }

    @Test
    fun reveal_transparentDerivationSucceeds_publishesTransparentKey() = runTest(dispatcher) {
        coEvery { zcashKeyExporter.export(any(), ZcashPrivateKeyType.Transparent) } returns TRANSPARENT_KEY
        val viewModel = createViewModel()

        viewModel.reveal(ZcashPrivateKeyType.Transparent)
        advanceUntilIdle()

        assertEquals(
            RevealedKey(ZcashPrivateKeyType.Transparent, TRANSPARENT_KEY),
            viewModel.uiState.revealed
        )
        assertNull(viewModel.uiState.showError)
    }

    @Test
    fun reveal_transparentDerivationFails_reportsTransparentError() = runTest(dispatcher) {
        coEvery { zcashKeyExporter.export(any(), ZcashPrivateKeyType.Transparent) } returns null
        val viewModel = createViewModel()

        viewModel.reveal(ZcashPrivateKeyType.Transparent)
        advanceUntilIdle()

        assertNull(viewModel.uiState.revealed)
        assertEquals(ZcashPrivateKeyType.Transparent, viewModel.uiState.showError)
    }

    @Test
    fun reveal_shieldedDerivationSucceeds_publishesKeyWithoutError() = runTest(dispatcher) {
        coEvery { zcashKeyExporter.export(any(), ZcashPrivateKeyType.Shielded) } returns SHIELDED_KEY
        val viewModel = createViewModel()

        viewModel.reveal(ZcashPrivateKeyType.Shielded)
        advanceUntilIdle()

        assertEquals(RevealedKey(ZcashPrivateKeyType.Shielded, SHIELDED_KEY), viewModel.uiState.revealed)
        assertNull(viewModel.uiState.showError)
    }

    @Test
    fun reveal_shieldedDerivationFails_reportsError() = runTest(dispatcher) {
        coEvery { zcashKeyExporter.export(any(), ZcashPrivateKeyType.Shielded) } returns null
        val viewModel = createViewModel()

        viewModel.reveal(ZcashPrivateKeyType.Shielded)
        advanceUntilIdle()

        assertNull(viewModel.uiState.revealed)
        assertEquals(ZcashPrivateKeyType.Shielded, viewModel.uiState.showError)
    }

    @Test
    fun onErrorShown_afterShieldedFailure_clearsTheError() = runTest(dispatcher) {
        coEvery { zcashKeyExporter.export(any(), ZcashPrivateKeyType.Shielded) } returns null
        val viewModel = createViewModel()
        viewModel.reveal(ZcashPrivateKeyType.Shielded)
        advanceUntilIdle()
        assertEquals(ZcashPrivateKeyType.Shielded, viewModel.uiState.showError)

        viewModel.onErrorShown()

        assertNull(viewModel.uiState.showError)
    }

    @Test
    fun reveal_shieldedRetriedAfterFailure_derivesAgain() = runTest(dispatcher) {
        coEvery { zcashKeyExporter.export(any(), ZcashPrivateKeyType.Shielded) } returns null
        val viewModel = createViewModel()
        viewModel.reveal(ZcashPrivateKeyType.Shielded)
        advanceUntilIdle()
        viewModel.onErrorShown()

        coEvery { zcashKeyExporter.export(any(), ZcashPrivateKeyType.Shielded) } returns SHIELDED_KEY
        viewModel.reveal(ZcashPrivateKeyType.Shielded)
        advanceUntilIdle()

        assertEquals(SHIELDED_KEY, viewModel.uiState.revealed?.key)
    }

    @Test
    fun cancelReveal_afterShieldedDerivation_dropsPendingKey() = runTest(dispatcher) {
        coEvery { zcashKeyExporter.export(any(), ZcashPrivateKeyType.Shielded) } returns SHIELDED_KEY
        val viewModel = createViewModel()
        viewModel.reveal(ZcashPrivateKeyType.Shielded)
        advanceUntilIdle()

        viewModel.cancelReveal()

        assertNull(viewModel.uiState.revealed)
    }

    @Test
    fun cancelReveal_whileDerivingShielded_neverPublishesKey() = runTest(dispatcher) {
        coEvery { zcashKeyExporter.export(any(), ZcashPrivateKeyType.Shielded) } coAnswers {
            delay(1_000)
            SHIELDED_KEY
        }
        val viewModel = createViewModel()
        viewModel.reveal(ZcashPrivateKeyType.Shielded)

        viewModel.cancelReveal()
        advanceUntilIdle()

        assertNull(viewModel.uiState.revealed)
    }

    @Test
    fun cancelReveal_afterShieldedFailure_clearsPendingError() = runTest(dispatcher) {
        coEvery { zcashKeyExporter.export(any(), ZcashPrivateKeyType.Shielded) } returns null
        val viewModel = createViewModel()
        viewModel.reveal(ZcashPrivateKeyType.Shielded)
        advanceUntilIdle()

        viewModel.cancelReveal()

        assertNull(viewModel.uiState.showError)
    }

    @Test
    fun cancelReveal_whileDerivingShielded_doesNotReportFalseError() = runTest(dispatcher) {
        coEvery { zcashKeyExporter.export(any(), ZcashPrivateKeyType.Shielded) } coAnswers {
            withContext(NonCancellable) { delay(1_000) }
            SHIELDED_KEY
        }
        val viewModel = createViewModel()
        viewModel.reveal(ZcashPrivateKeyType.Shielded)

        viewModel.cancelReveal()
        advanceUntilIdle()

        assertNull(viewModel.uiState.revealed)
        assertNull(viewModel.uiState.showError)
    }

    @Test
    fun reveal_sameTypeTappedTwice_startsOnlyOneDerivationAndStillPublishesKey() = runTest(dispatcher) {
        coEvery { zcashKeyExporter.export(any(), ZcashPrivateKeyType.Shielded) } coAnswers {
            delay(1_000)
            SHIELDED_KEY
        }
        val viewModel = createViewModel()

        viewModel.reveal(ZcashPrivateKeyType.Shielded)
        viewModel.reveal(ZcashPrivateKeyType.Shielded)
        advanceUntilIdle()

        coVerify(exactly = 1) { zcashKeyExporter.export(any(), ZcashPrivateKeyType.Shielded) }
        assertEquals(
            RevealedKey(ZcashPrivateKeyType.Shielded, SHIELDED_KEY),
            viewModel.uiState.revealed
        )
    }

    @Test
    fun reveal_otherTypeWhileOneInFlight_startsSecondDerivation() = runTest(dispatcher) {
        coEvery { zcashKeyExporter.export(any(), ZcashPrivateKeyType.Shielded) } coAnswers {
            delay(1_000)
            SHIELDED_KEY
        }
        coEvery { zcashKeyExporter.export(any(), ZcashPrivateKeyType.Transparent) } returns TRANSPARENT_KEY
        val viewModel = createViewModel()

        viewModel.reveal(ZcashPrivateKeyType.Shielded)
        viewModel.reveal(ZcashPrivateKeyType.Transparent)
        advanceUntilIdle()

        coVerify { zcashKeyExporter.export(any(), ZcashPrivateKeyType.Transparent) }
    }

    @Test
    fun reveal_saplingSpendingAccount_publishesStoredKeyWithoutSdkCall() = runTest(dispatcher) {
        mockkStatic(ZCASH_SDK_EXTENSIONS)
        val viewModel = createViewModel(AccountType.ZCashSaplingKey(SAPLING_SPENDING_KEY))

        viewModel.reveal(ZcashPrivateKeyType.Shielded)
        advanceUntilIdle()

        try {
            assertEquals(
                RevealedKey(ZcashPrivateKeyType.Shielded, SAPLING_SPENDING_KEY),
                viewModel.uiState.revealed
            )
            coVerify(exactly = 0) { ZcashSdk.deriveSpendingKey(any(), any(), any(), any()) }
            coVerify(exactly = 0) { ZcashSdk.deriveTransparentAccountKey(any(), any(), any()) }
            coVerify(exactly = 0) { ZcashSdk.deriveSaplingViewingKey(any(), any()) }
        } finally {
            unmockkStatic(ZCASH_SDK_EXTENSIONS)
        }
    }

    @Test
    fun reveal_otherTypeSucceedsWhileErrorPending_keepsThatError() = runTest(dispatcher) {
        coEvery { zcashKeyExporter.export(any(), ZcashPrivateKeyType.Shielded) } returns null
        coEvery { zcashKeyExporter.export(any(), ZcashPrivateKeyType.Transparent) } returns TRANSPARENT_KEY
        val viewModel = createViewModel()
        viewModel.reveal(ZcashPrivateKeyType.Shielded)
        advanceUntilIdle()
        assertEquals(ZcashPrivateKeyType.Shielded, viewModel.uiState.showError)

        viewModel.reveal(ZcashPrivateKeyType.Transparent)
        advanceUntilIdle()

        assertEquals(
            RevealedKey(ZcashPrivateKeyType.Transparent, TRANSPARENT_KEY),
            viewModel.uiState.revealed,
        )
        assertEquals(ZcashPrivateKeyType.Shielded, viewModel.uiState.showError)
    }

    private fun createViewModel(type: AccountType = AccountType.Mnemonic(WORDS, "")): ZcashKeysViewModel {
        every { accountManager.account(ACCOUNT_ID) } returns Account(
            id = ACCOUNT_ID,
            name = "name",
            type = type,
            origin = AccountOrigin.Restored,
            level = 0
        )
        return ZcashKeysViewModel(ACCOUNT_ID, accountManager, zcashKeyExporter)
    }

    private companion object {
        const val ZCASH_SDK_EXTENSIONS = "cash.p.zcash.ZcashSdkKt"
        const val ACCOUNT_ID = "id"
        const val SHIELDED_KEY = "secret-extended-key-main1qtest"
        const val TRANSPARENT_KEY = "xprv-transparent-test"
        const val SAPLING_SPENDING_KEY = "secret-extended-key-main1qsaplingspendingkey"

        val WORDS = List(12) { "abandon" }
    }
}
