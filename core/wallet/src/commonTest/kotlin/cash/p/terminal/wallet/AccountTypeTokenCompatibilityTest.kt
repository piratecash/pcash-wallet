package cash.p.terminal.wallet

import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import org.junit.Test
import java.math.BigInteger
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountTypeTokenCompatibilityTest {

    private val nonMnemonicAccountTypes: List<AccountType> = listOf(
        AccountType.EvmAddress("0x0000000000000000000000000000000000000000"),
        AccountType.SolanaAddress("solana-address"),
        AccountType.TronAddress("tron-address"),
        AccountType.TonAddress("ton-address"),
        AccountType.StellarAddress("stellar-address"),
        AccountType.BitcoinAddress("bc1address", BlockchainType.Bitcoin, TokenType.Native),
        AccountType.StellarSecretKey("secret-key"),
        AccountType.MnemonicMonero(listOf("word"), "", 0L, "wallet"),
        AccountType.EvmPrivateKey(BigInteger.ONE),
        AccountType.HdExtendedKey("serialized-key"),
        AccountType.ZCashUfvKey("ufvk"),
        AccountType.HardwareCard("card-id", 0, "public-key", 0),
        AccountType.TrezorDevice("device-id", "model", "1.0.0", "public-key"),
    )

    private val thorchainMayachain = listOf(BlockchainType.Thorchain, BlockchainType.Mayachain)

    @Test
    fun isCompatibleWith_nonMnemonicAccountTypes_returnsFalseForThorchainAndMayachain() {
        for (blockchainType in thorchainMayachain) {
            for (accountType in nonMnemonicAccountTypes) {
                assertFalse(
                    accountType.isCompatibleWith(blockchainType, TokenType.Native),
                    "$accountType should not be compatible with $blockchainType",
                )
            }
        }
    }

    @Test
    fun isCompatibleWith_mnemonicAccountType_returnsTrueForThorchainAndMayachain() {
        val mnemonic = AccountType.Mnemonic(listOf("word"), "")

        for (blockchainType in thorchainMayachain) {
            assertTrue(mnemonic.isCompatibleWith(blockchainType, TokenType.Native))
        }
    }

    private val thorchainAddress = AccountType.ThorchainAddress("thor-address")
    private val mayachainAddress = AccountType.MayachainAddress("maya-address")

    private val foreignNetworks = listOf(
        BlockchainType.Stellar,
        BlockchainType.Bitcoin,
        BlockchainType.Ethereum,
        BlockchainType.Solana,
        BlockchainType.Ton,
    )

    @Test
    fun isCompatibleWith_thorchainAddress_returnsTrueForThorchainTokensOnly() {
        assertTrue(thorchainAddress.isCompatibleWith(BlockchainType.Thorchain, TokenType.Native))
        assertTrue(thorchainAddress.isCompatibleWith(BlockchainType.Thorchain, TokenType.ThorchainAsset("tcy")))
        assertFalse(thorchainAddress.isCompatibleWith(BlockchainType.Mayachain, TokenType.Native))
    }

    @Test
    fun isCompatibleWith_mayachainAddress_returnsTrueForMayachainTokensOnly() {
        assertTrue(mayachainAddress.isCompatibleWith(BlockchainType.Mayachain, TokenType.Native))
        assertTrue(mayachainAddress.isCompatibleWith(BlockchainType.Mayachain, TokenType.ThorchainAsset("maya")))
        assertFalse(mayachainAddress.isCompatibleWith(BlockchainType.Thorchain, TokenType.Native))
    }

    @Test
    fun isCompatibleWith_thorchainAndMayachainAddressOnForeignNetwork_returnsFalse() {
        for (accountType in listOf(thorchainAddress, mayachainAddress)) {
            for (blockchainType in foreignNetworks) {
                assertFalse(
                    accountType.isCompatibleWith(blockchainType, TokenType.Native),
                    "$accountType should not be compatible with $blockchainType",
                )
            }
        }
    }
}
