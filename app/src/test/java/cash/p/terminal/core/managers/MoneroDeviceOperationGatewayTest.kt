package cash.p.terminal.core.managers

import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneroDeviceOperationGatewayTest {

    private val trezor = mockk<MoneroTrezorOperationGateway>(relaxed = true)
    private val ledger = mockk<MoneroLedgerOperationGateway>(relaxed = true)
    private val gateway = MoneroDeviceOperationGateway(trezor, ledger)

    private val trezorAccount = account(
        AccountType.TrezorDevice(
            deviceId = "trezor-id",
            model = "T3T1",
            firmwareVersion = "2.8.10",
            walletPublicKey = "trezor-key",
        ),
    )
    private val ledgerAccount = account(
        AccountType.LedgerDevice(deviceId = "ledger-id", model = "NanoSPlus", walletPublicKey = "ledger-key"),
    )

    @Test
    fun isUserOperationWaiting_trezorUserOperationWaiting_isTrue() {
        every { trezor.isUserOperationWaiting } returns true

        assertTrue(gateway.isUserOperationWaiting)
    }

    @Test
    fun retainSessions_accountIds_forwardsToTrezor() {
        gateway.retainSessions(setOf("kept"))

        verify(exactly = 1) { trezor.retainSessions(setOf("kept")) }
    }

    @Test
    fun markLiveRefreshConfirmed_trezorAccount_forwardsToTrezorOnly() {
        gateway.markLiveRefreshConfirmed(trezorAccount)

        verify(exactly = 1) { trezor.markLiveRefreshConfirmed(trezorAccount) }
        verify(exactly = 0) { ledger.markLiveRefreshConfirmed(any()) }
    }

    @Test
    fun markLiveRefreshConfirmed_ledgerAccount_forwardsToLedgerOnly() {
        gateway.markLiveRefreshConfirmed(ledgerAccount)

        verify(exactly = 1) { ledger.markLiveRefreshConfirmed(ledgerAccount) }
        verify(exactly = 0) { trezor.markLiveRefreshConfirmed(any()) }
    }

    private fun account(type: AccountType) = Account(
        id = "account-id",
        name = "Monero",
        type = type,
        origin = AccountOrigin.Created,
        level = 0,
    )
}
