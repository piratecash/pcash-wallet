package cash.p.terminal.core.usecase

import cash.p.terminal.core.IAccountFactory
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.managers.WalletActivator
import cash.p.terminal.ledger.domain.LedgerAppMissingException
import cash.p.terminal.ledger.domain.LedgerConnector
import cash.p.terminal.ledger.domain.LedgerDeviceId
import cash.p.terminal.ledger.domain.LedgerIdentityMismatchException
import cash.p.terminal.ledger.domain.LedgerKey
import cash.p.terminal.ledger.domain.LedgerKeyRead
import cash.p.terminal.ledger.domain.LedgerKeyReader
import cash.p.terminal.ledger.domain.LedgerRejectedException
import cash.p.terminal.ledger.domain.LedgerSkippedApp
import cash.p.terminal.ledger.monero.LEDGER_MONERO_APP
import cash.p.terminal.ledgerkit.device.LedgerModel
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IHardwarePublicKeyStorage
import cash.p.terminal.wallet.entities.HardwarePublicKey
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.hardware.HardwarePublicKeySpecs
import cash.p.terminal.wallet.hardware.HardwareVendor
import cash.p.terminal.wallet.monero
import com.piratecash.monero.signer.HardwareWalletErrorCode
import com.piratecash.monero.signer.HardwareWalletOperationException
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class CreateLedgerWalletUseCaseTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val ethereum = TokenQuery(BlockchainType.Ethereum, TokenType.Native)
    private val bsc = TokenQuery(BlockchainType.BinanceSmartChain, TokenType.Native)
    private val bitcoin = TokenQuery(BlockchainType.Bitcoin, TokenType.Derived(TokenType.Derivation.Bip84))
    private val moneroFirstQueries =
        listOf(TokenQuery.monero) + TokenQuery.defaultTokenQueries.filterNot { it == TokenQuery.monero }
    private val keyReader = mockk<LedgerKeyReader>()
    private val connector = mockk<LedgerConnector> { every { model } returns LedgerModel.NanoX }
    private val accountManager = mockk<IAccountManager>(relaxed = true)
    private val hardwarePublicKeyStorage = mockk<IHardwarePublicKeyStorage>(relaxed = true)
    private val walletActivator = mockk<WalletActivator>(relaxed = true)
    private val addMoneroToAccount = mockk<AddMoneroToTrezorAccountUseCase>(relaxed = true)
    private val accountType = slot<AccountType>()
    private val accountFactory = mockk<IAccountFactory> {
        every { account(any(), capture(accountType), any(), any(), any()) } answers {
            Account(ACCOUNT_ID, firstArg(), accountType.captured, AccountOrigin.Created, level = 0)
        }
    }

    @Test
    fun invoke_keysRead_createsLedgerAccountIdentifiedByFirstKey() = runTest(dispatcher) {
        readReturns(LedgerKeyRead(listOf(key(ethereum, "eth-key"), key(bsc, "bsc-key")), emptyList()))
        val savedKeys = slot<List<HardwarePublicKey>>()
        coEvery { hardwarePublicKeyStorage.save(capture(savedKeys)) } returns Unit

        val creation = useCase()("Ledger", {}, {})

        assertTrue(creation.created)
        assertEquals(
            AccountType.LedgerDevice(LedgerDeviceId.fromWalletPublicKey("eth-key"), "NanoX", "eth-key"),
            accountType.captured,
        )
        assertEquals(listOf("eth-key", "bsc-key"), savedKeys.captured.map { it.key.value })
        assertTrue(savedKeys.captured.all { it.accountId == ACCOUNT_ID })
        verify { accountManager.save(match { it.id == ACCOUNT_ID }, false) }
        coVerify { walletActivator.activateWalletsSuspended(match { it.id == ACCOUNT_ID }, listOf(ethereum, bsc)) }
        verify { accountManager.setActiveAccountId(ACCOUNT_ID) }
    }

    @Test
    fun invoke_always_readsMoneroFirst() = runTest(dispatcher) {
        readReturns(LedgerKeyRead(emptyList(), emptyList()))

        useCase()("Ledger", {}, {})

        coVerify { keyReader.read(moneroFirstQueries, null, any()) }
    }

    @Test
    fun invoke_moneroKeyReadFirst_identifiesAccountByBitcoinKey() = runTest(dispatcher) {
        readReturns(
            LedgerKeyRead(listOf(key(TokenQuery.monero, "xmr-address"), key(bitcoin, BITCOIN_ZPUB)), emptyList()),
        )

        useCase()("Ledger", {}, {})

        assertEquals(
            AccountType.LedgerDevice(LedgerDeviceId.fromWalletPublicKey(BITCOIN_ZPUB), "NanoX", BITCOIN_ZPUB),
            accountType.captured,
        )
    }

    @Test
    fun invoke_someAppsSkipped_createsAccountAndReportsThem() = runTest(dispatcher) {
        readReturns(
            LedgerKeyRead(
                listOf(key(ethereum, "eth-key")),
                listOf(
                    LedgerSkippedApp("Bitcoin", LedgerAppMissingException("Bitcoin")),
                    LedgerSkippedApp("Zcash", LedgerAppMissingException("Zcash")),
                ),
            ),
        )

        val creation = useCase()("Ledger", {}, {})

        assertTrue(creation.created)
        assertEquals(listOf("Bitcoin", "Zcash"), creation.skippedApps)
    }

    @Test
    fun invoke_noKeysRead_createsNothing() = runTest(dispatcher) {
        readReturns(
            LedgerKeyRead(emptyList(), listOf(LedgerSkippedApp("Ethereum", LedgerAppMissingException("Ethereum")))),
        )

        val creation = useCase()("Ledger", {}, {})

        assertFalse(creation.created)
        assertEquals(listOf("Ethereum"), creation.skippedApps)
        verify(exactly = 0) { accountManager.save(any(), any()) }
        coVerify(exactly = 0) { hardwarePublicKeyStorage.save(any()) }
    }

    @Test
    fun invoke_anotherLedgerAnswersMidway_failsWithoutSavingAccount() = runTest(dispatcher) {
        coEvery { keyReader.read(any(), null, any()) } throws LedgerIdentityMismatchException()

        assertFailsWith<LedgerIdentityMismatchException> { useCase()("Ledger", {}, {}) }

        verify(exactly = 0) { accountManager.save(any(), any()) }
        coVerify(exactly = 0) { hardwarePublicKeyStorage.save(any()) }
    }

    @Test
    fun invoke_identityReopenRejected_failsWithoutSavingAccount() = runTest(dispatcher) {
        coEvery { keyReader.read(any(), null, any()) } throws LedgerRejectedException()

        assertFailsWith<LedgerRejectedException> { useCase()("Ledger", {}, {}) }

        verify(exactly = 0) { accountManager.save(any(), any()) }
        coVerify(exactly = 0) { hardwarePublicKeyStorage.save(any()) }
    }

    @Test
    fun invoke_moneroKeyRead_createsMoneroWalletAfterStoringKeys() = runTest(dispatcher) {
        readReturns(LedgerKeyRead(listOf(key(ethereum, "eth-key"), key(TokenQuery.monero, "xmr-address")), emptyList()))
        var exportingViewKey = false

        val creation = useCase()("Ledger", {}, { exportingViewKey = true })

        assertTrue(creation.created)
        assertTrue(exportingViewKey)
        assertEquals(emptyList<String>(), creation.skippedApps)
        coVerifyOrder {
            hardwarePublicKeyStorage.save(match { keys -> keys.any { it.key.value == "xmr-address" } })
            walletActivator.activateWalletsSuspended(match { it.id == ACCOUNT_ID }, listOf(ethereum))
            addMoneroToAccount(match { it.id == ACCOUNT_ID })
        }
    }

    @Test
    fun invoke_moneroWalletNotCreated_keepsAccountAndReportsMonero() = runTest(dispatcher) {
        readReturns(LedgerKeyRead(listOf(key(ethereum, "eth-key"), key(TokenQuery.monero, "xmr-address")), emptyList()))
        coEvery { addMoneroToAccount(any()) } throws
            HardwareWalletOperationException(HardwareWalletErrorCode.ViewKeyExportDenied, null)

        val creation = useCase()("Ledger", {}, {})

        assertTrue(creation.created)
        assertEquals(listOf(LEDGER_MONERO_APP), creation.skippedApps)
        verify { accountManager.setActiveAccountId(ACCOUNT_ID) }
    }

    private fun readReturns(read: LedgerKeyRead) {
        coEvery { keyReader.read(moneroFirstQueries, null, any()) } returns read
    }

    private fun key(query: TokenQuery, key: String) =
        LedgerKey(checkNotNull(HardwarePublicKeySpecs.specFor(query, HardwareVendor.Ledger)), key, byteArrayOf(1))

    private fun useCase() = CreateLedgerWalletUseCase(
        keyReader = keyReader,
        connector = connector,
        accountFactory = accountFactory,
        accountManager = accountManager,
        accountActivator = HardwareAccountActivator(
            accountManager = accountManager,
            hardwarePublicKeyStorage = hardwarePublicKeyStorage,
            walletActivator = walletActivator,
            dispatcherProvider = TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher)),
        ),
        addMoneroToAccount = addMoneroToAccount,
    )

    private companion object {
        const val ACCOUNT_ID = "ledger-account"

        // BIP84 test vector account key.
        const val BITCOIN_ZPUB =
            "zpub6rFR7y4Q2AijBEqTUquhVz398htDFrtymD9xYYfG1m4wAcvPhXNfE3EfH1r1" +
                "ADqtfSdVCToUG868RvUUkgDKf31mGDtKsAYz2oz2AGutZYs"
    }
}
