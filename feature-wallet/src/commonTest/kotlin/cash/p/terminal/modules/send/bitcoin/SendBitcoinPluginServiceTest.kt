package cash.p.terminal.modules.send.bitcoin

import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SendBitcoinPluginServiceTest {

    @Test
    fun isLockTimeEnabled_bitcoinMnemonic_true() {
        assertTrue(service(BlockchainType.Bitcoin, mnemonic()).isLockTimeEnabled)
    }

    @Test
    fun isLockTimeEnabled_bitcoinTrezor_false() {
        val trezor = AccountType.TrezorDevice("id", "T3T1", "2.8.1", "key")

        assertFalse(service(BlockchainType.Bitcoin, trezor).isLockTimeEnabled)
    }

    @Test
    fun isLockTimeEnabled_bitcoinLedger_false() {
        assertFalse(service(BlockchainType.Bitcoin, AccountType.LedgerDevice("id", "nanoSP", "key")).isLockTimeEnabled)
    }

    @Test
    fun isLockTimeEnabled_litecoinMnemonic_false() {
        assertFalse(service(BlockchainType.Litecoin, mnemonic()).isLockTimeEnabled)
    }

    private fun mnemonic() = AccountType.Mnemonic(List(12) { "word$it" }, "")

    private fun service(blockchainType: BlockchainType, accountType: AccountType) =
        SendBitcoinPluginService(
            mockk<Wallet> {
                every { token.blockchainType } returns blockchainType
                every { account.type } returns accountType
            }
        )
}
