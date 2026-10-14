package cash.p.terminal.wallet

import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.policy.HardwareWalletTokenPolicy
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.mockk
import org.junit.Test
import java.math.BigInteger
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WalletFactoryTest {

    private val walletFactory = WalletFactory(mockk<HardwareWalletTokenPolicy>(relaxed = true))

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

    private fun token(blockchainType: BlockchainType) = Token(
        coin = Coin(uid = "test-coin", name = "Test Coin", code = "TEST"),
        blockchain = Blockchain(type = blockchainType, name = blockchainType.uid, eip3091url = null),
        type = TokenType.Native,
        decimals = 8,
    )

    private fun account(accountType: AccountType) = Account(
        id = "account-id",
        name = "Account",
        type = accountType,
        origin = AccountOrigin.Created,
        level = 0,
    )

    @Test
    fun create_nonMnemonicAccountTypesOnThorchainAndMayachain_returnsNull() {
        for (blockchainType in listOf(BlockchainType.Thorchain, BlockchainType.Mayachain)) {
            val walletToken = token(blockchainType)
            for (accountType in nonMnemonicAccountTypes) {
                val wallet = walletFactory.create(walletToken, account(accountType), null)

                assertNull(wallet, "$accountType should not produce a wallet on $blockchainType")
            }
        }
    }

    @Test
    fun create_mnemonicAccountTypeOnThorchainAndMayachain_returnsWallet() {
        val mnemonic = AccountType.Mnemonic(listOf("word"), "")

        for (blockchainType in listOf(BlockchainType.Thorchain, BlockchainType.Mayachain)) {
            val wallet = walletFactory.create(token(blockchainType), account(mnemonic), null)

            assertNotNull(wallet)
        }
    }
}
