package cash.p.terminal.wallet

import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class AccountTypeTokenCompatibilityTest(private val accountType: AccountType) {

    @Test
    fun isCompatibleWith_nativeBeam_allowsOnlyMnemonic() {
        assertEquals(
            accountType is AccountType.Mnemonic,
            accountType.isCompatibleWith(BlockchainType.Beam, TokenType.Native)
        )
    }

    @Test
    fun isCompatibleWith_nonNativeBeam_rejectsToken() {
        assertFalse(
            accountType.isCompatibleWith(BlockchainType.Beam, TokenType.Eip20("contract"))
        )
    }

    @Test
    fun isCompatibleWith_legacyGameTokenOnBeamUid_rejectsToken() {
        assertFalse(
            accountType.isCompatibleWith(
                BlockchainType.Beam,
                TokenType.Unsupported("beam", "0x76bf5e7d2bcb06b1444c0a2742780051d8d0e304")
            )
        )
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters
        fun accounts(): List<Array<AccountType>> = listOf<AccountType>(
            mockk<AccountType.Mnemonic>(),
            mockk<AccountType.MnemonicMonero>(),
            mockk<AccountType.HardwareCard>(),
            mockk<AccountType.TrezorDevice>(),
            mockk<AccountType.HdExtendedKey>(),
            mockk<AccountType.BitcoinAddress>(),
            mockk<AccountType.EvmAddress>(),
            mockk<AccountType.EvmPrivateKey>(),
            mockk<AccountType.SolanaAddress>(),
            mockk<AccountType.TronAddress>(),
            mockk<AccountType.TonAddress>(),
            mockk<AccountType.StellarAddress>(),
            mockk<AccountType.StellarSecretKey>(),
            mockk<AccountType.ZCashUfvKey>()
        ).map { arrayOf(it) }
    }
}
