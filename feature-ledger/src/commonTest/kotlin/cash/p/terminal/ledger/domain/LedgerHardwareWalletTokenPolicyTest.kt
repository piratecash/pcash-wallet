package cash.p.terminal.ledger.domain

import cash.p.terminal.ledger.FakeKeyFetcher
import cash.p.terminal.ledger.FakeLedgerDevice
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LedgerHardwareWalletTokenPolicyTest {

    private val ethereum = TokenQuery(BlockchainType.Ethereum, TokenType.Native)
    private val device = FakeLedgerDevice(installedApps = emptyMap())
    private val policy = LedgerHardwareWalletTokenPolicy(
        LedgerAppKeyFetchers(listOf(FakeKeyFetcher("Ethereum", device, mapOf(ethereum to "key"))))
    )

    @Test
    fun isSupported_tokenWithAppFetcher_returnsTrue() {
        assertTrue(policy.isSupported(BlockchainType.Ethereum, TokenType.Native))
    }

    @Test
    fun isSupported_tokenWithoutAppFetcher_returnsFalse() {
        assertFalse(policy.isSupported(BlockchainType.Tron, TokenType.Native))
    }

    @Test
    fun isSupported_noFetchersRegistered_rejectsEveryToken() {
        val emptyPolicy = LedgerHardwareWalletTokenPolicy(LedgerAppKeyFetchers(emptyList()))

        assertFalse(emptyPolicy.isSupported(BlockchainType.Ethereum, TokenType.Native))
    }

    @Test
    fun isSupportedForAccount_nonLedgerAccount_returnsFalse() {
        val account = mockk<Account> { every { type } returns AccountType.TrezorDevice("id", "T3T1", "1.0", "key") }

        assertFalse(policy.isSupported(account, ethereumToken()))
    }

    @Test
    fun isSupportedForAccount_ledgerAccount_followsAppSupport() {
        val account = mockk<Account> { every { type } returns AccountType.LedgerDevice("id", "NanoX", "key") }

        assertTrue(policy.isSupported(account, ethereumToken()))
    }

    private fun ethereumToken() = mockk<Token> {
        every { blockchainType } returns BlockchainType.Ethereum
        every { type } returns TokenType.Native
    }
}
