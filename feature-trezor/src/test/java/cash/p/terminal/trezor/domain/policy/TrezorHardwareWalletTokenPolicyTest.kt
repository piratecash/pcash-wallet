package cash.p.terminal.trezor.domain.policy

import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrezorHardwareWalletTokenPolicyTest {

    private val policy = TrezorHardwareWalletTokenPolicy()

    @Test
    fun isSupported_zcashTransparent_admitted() {
        assertTrue(policy.isSupported(account(), zecToken(TokenType.AddressSpecType.Transparent)))
    }

    @Test
    fun isSupported_zcashShielded_refused() {
        assertFalse(policy.isSupported(account(), zecToken(TokenType.AddressSpecType.Shielded)))
    }

    @Test
    fun isSupported_zcashUnified_refused() {
        assertFalse(policy.isSupported(account(), zecToken(TokenType.AddressSpecType.Unified)))
    }

    @Test
    fun isSupported_zcashLegacyNativeTokenType_refused() {
        assertFalse(policy.isSupported(account(), zecToken(TokenType.Native)))
    }

    private fun account() = Account(
        id = "acc-1",
        name = "Trezor",
        type = AccountType.TrezorDevice(
            deviceId = "dev-1",
            model = "T3B1",
            firmwareVersion = "2.8.7",
            walletPublicKey = "wallet-public-key",
        ),
        origin = AccountOrigin.Restored,
        level = 0,
    )

    private fun zecToken(spec: TokenType.AddressSpecType) = zecToken(TokenType.AddressSpecTyped(spec))

    private fun zecToken(tokenType: TokenType) = mockk<Token> {
        every { this@mockk.blockchainType } returns BlockchainType.Zcash
        every { this@mockk.type } returns tokenType
    }
}
