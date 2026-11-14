package cash.p.terminal.core.policy

import cash.p.terminal.core.usecase.AddMoneroToTrezorAccountUseCase
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.useCases.ScanToAddUseCase
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.test.assertFailsWith

class CompositeScanToAddUseCaseTest {
    private val accountManager = mockk<IAccountManager>()
    private val tangemScanToAdd = mockk<ScanToAddUseCase>()
    private val trezorScanToAdd = mockk<ScanToAddUseCase>()
    private val ledgerScanToAdd = mockk<ScanToAddUseCase>()
    private val addMonero = mockk<AddMoneroToTrezorAccountUseCase>()
    private val useCase = CompositeScanToAddUseCase(
        accountManager,
        tangemScanToAdd,
        trezorScanToAdd,
        ledgerScanToAdd,
        addMonero,
    )

    @Test
    fun addTokensByScan_ledgerAccount_routesToLedgerScan() = runTest {
        val ledger = Account(
            id = ACCOUNT_ID,
            name = "Ledger",
            type = AccountType.LedgerDevice(DEVICE_ID, "NanoX", "wallet-key"),
            origin = AccountOrigin.Created,
            level = 0,
        )
        val ethereum = TokenQuery(BlockchainType.Ethereum, TokenType.Native)
        every { accountManager.account(ACCOUNT_ID) } returns ledger
        coEvery { ledgerScanToAdd.addTokensByScan(listOf(ethereum), DEVICE_ID, ACCOUNT_ID) } returns true

        val result = useCase.addTokensByScan(listOf(ethereum), DEVICE_ID, ACCOUNT_ID)

        assertTrue(result)
        coVerify(exactly = 0) { trezorScanToAdd.addTokensByScan(any(), any(), any()) }
        coVerify(exactly = 0) { tangemScanToAdd.addTokensByScan(any(), any(), any()) }
    }

    @Test
    fun addTokensByScan_ledgerMonero_provisionsWallet() = runTest {
        val ledger = Account(
            id = ACCOUNT_ID,
            name = "Ledger",
            type = AccountType.LedgerDevice(DEVICE_ID, "NanoX", "wallet-key"),
            origin = AccountOrigin.Created,
            level = 0,
        )
        every { accountManager.account(ACCOUNT_ID) } returns ledger
        coEvery { ledgerScanToAdd.addTokensByScan(listOf(moneroQuery), DEVICE_ID, ACCOUNT_ID) } returns true
        coEvery { addMonero.provision(ledger) } returns ledger

        useCase.addTokensByScan(listOf(moneroQuery), DEVICE_ID, ACCOUNT_ID)

        coVerify(exactly = 1) { addMonero.provision(ledger) }
    }

    @Test
    fun addTokensByScan_targetTrezorIsNotActive_addsMoneroToTargetAccount() = runTest {
        val target = trezorAccount(ACCOUNT_ID, DEVICE_ID)
        every { accountManager.account(ACCOUNT_ID) } returns target
        coEvery { addMonero(target) } returns Unit

        val result = useCase.addTokensByScan(
            blockchainsToDerive = listOf(moneroQuery),
            cardId = DEVICE_ID,
            accountId = ACCOUNT_ID,
        )

        assertTrue(result)
        coVerify(exactly = 1) { addMonero(target) }
        coVerify(exactly = 0) { tangemScanToAdd.addTokensByScan(any(), any(), any()) }
    }

    @Test
    fun addTokensByScan_deviceDoesNotMatchAccount_rejectsRequest() = runTest {
        every { accountManager.account(ACCOUNT_ID) } returns trezorAccount(ACCOUNT_ID, DEVICE_ID)

        assertFailsWith<IllegalStateException> {
            useCase.addTokensByScan(
                blockchainsToDerive = listOf(moneroQuery),
                cardId = "other-device",
                accountId = ACCOUNT_ID,
            )
        }

        coVerify(exactly = 0) { addMonero(any()) }
    }

    private fun trezorAccount(id: String, deviceId: String) = Account(
        id = id,
        name = "Trezor",
        type = AccountType.TrezorDevice(
            deviceId = deviceId,
            model = "T3T1",
            firmwareVersion = "2.8.10",
            walletPublicKey = "wallet-key",
        ),
        origin = AccountOrigin.Created,
        level = 0,
    )

    private companion object {
        const val ACCOUNT_ID = "target-account"
        const val DEVICE_ID = "device-id"
        val moneroQuery = TokenQuery(BlockchainType.Monero, TokenType.Native)
    }
}
