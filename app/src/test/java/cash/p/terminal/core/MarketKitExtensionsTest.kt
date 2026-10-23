package cash.p.terminal.core

import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.FullCoin
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MarketKitExtensionsTest {

    private val ledger = AccountType.LedgerDevice("ledger-id", "nanoSP", "wallet-key")

    private fun nativeToken(blockchainType: BlockchainType) = token(blockchainType, TokenType.Native)

    private fun token(blockchainType: BlockchainType, type: TokenType) = Token(
        coin = Coin(uid = "test-coin", name = "Test Coin", code = "TEST"),
        blockchain = Blockchain(type = blockchainType, name = blockchainType.uid, eip3091url = null),
        type = type,
        decimals = 8,
    )

    @Test
    fun supports_ledgerDevice_onlyChainsWithLedgerKeyFetcher() {
        val supported = listOf(
            BlockchainType.Bitcoin, BlockchainType.Litecoin, BlockchainType.BitcoinCash, BlockchainType.Dogecoin,
            BlockchainType.Dash, BlockchainType.Ethereum, BlockchainType.BinanceSmartChain, BlockchainType.Polygon,
            BlockchainType.ArbitrumOne, BlockchainType.Optimism, BlockchainType.Base, BlockchainType.RobinhoodChain,
            BlockchainType.Avalanche, BlockchainType.Gnosis, BlockchainType.Fantom, BlockchainType.ZkSync,
            BlockchainType.Solana, BlockchainType.Tron, BlockchainType.Stellar, BlockchainType.Ton,
            BlockchainType.Thorchain, BlockchainType.Zcash, BlockchainType.Monero,
        )
        val unsupported = listOf(
            BlockchainType.Mayachain, BlockchainType.ECash,
            BlockchainType.Beam, BlockchainType.PirateCash, BlockchainType.Cosanta,
        )

        assertEquals(supported, supported.filter { it.supports(ledger) })
        assertEquals(emptyList(), unsupported.filter { it.supports(ledger) })
    }

    @Test
    fun supports_ledgerDeviceTokens_onlyTypesTheDeviceAppsSign() {
        val signed = listOf(
            token(BlockchainType.Ethereum, TokenType.Eip20("0xdac17f958d2ee523a2206206994597c13d831ec7")),
            token(BlockchainType.BinanceSmartChain, TokenType.Eip20("0x55d398326f99059ff775485246999027b3197955")),
            token(BlockchainType.Solana, TokenType.Spl("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v")),
            token(BlockchainType.Tron, TokenType.Eip20("TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t")),
            token(
                BlockchainType.Stellar,
                TokenType.Asset("USDC", "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN"),
            ),
            token(BlockchainType.Litecoin, TokenType.Derived(TokenType.Derivation.Bip84)),
            token(BlockchainType.Ton, TokenType.Jetton("EQCxE6mUtQJKFnGfaROTKOt1lZbDiiX1kCixRv7Nw2Id_sDs")),
            token(BlockchainType.Thorchain, TokenType.ThorchainAsset("btc-btc")),
            token(BlockchainType.Zcash, TokenType.AddressSpecTyped(TokenType.AddressSpecType.Transparent)),
            token(BlockchainType.Zcash, TokenType.AddressSpecTyped(TokenType.AddressSpecType.Unified)),
            nativeToken(BlockchainType.Monero),
        )
        val notSigned = listOf(
            token(BlockchainType.Tron, TokenType.Trc10("1002000")),
            token(BlockchainType.Litecoin, TokenType.Mweb),
            // The Zcash app has no Sapling.
            token(BlockchainType.Zcash, TokenType.AddressSpecTyped(TokenType.AddressSpecType.Shielded)),
            token(BlockchainType.Mayachain, TokenType.Native),
        )

        assertEquals(signed, signed.filter { it.supports(ledger) })
        assertEquals(emptyList(), notSigned.filter { it.supports(ledger) })
    }

    @Test
    fun isSupported_thorchainNative_returnsTrue() {
        assertTrue(TokenQuery(BlockchainType.Thorchain, TokenType.Native).isSupported)
    }

    @Test
    fun isSupported_thorchainAsset_returnsTrue() {
        assertTrue(
            TokenQuery(BlockchainType.Thorchain, TokenType.ThorchainAsset("rune")).isSupported
        )
    }

    @Test
    fun isSupported_mayachainNative_returnsTrue() {
        assertTrue(TokenQuery(BlockchainType.Mayachain, TokenType.Native).isSupported)
    }

    @Test
    fun isSupported_mayachainThorchainAsset_returnsFalse() {
        assertFalse(
            TokenQuery(BlockchainType.Mayachain, TokenType.ThorchainAsset("rune")).isSupported
        )
    }

    @Test
    fun supports_hardwareCardOnThorchainAndMayachain_returnsFalse() {
        val hardwareCard = AccountType.HardwareCard("card-id", 0, "public-key", 0)

        assertFalse(BlockchainType.Thorchain.supports(hardwareCard))
        assertFalse(BlockchainType.Mayachain.supports(hardwareCard))
    }

    @Test
    fun supports_thorchainAddress_returnsTrueOnlyForThorchain() {
        val thorchainAddress = AccountType.ThorchainAddress("thor1watched")

        assertTrue(BlockchainType.Thorchain.supports(thorchainAddress))
        assertFalse(BlockchainType.Mayachain.supports(thorchainAddress))
        assertFalse(BlockchainType.Stellar.supports(thorchainAddress))
    }

    @Test
    fun supports_mayachainAddress_returnsTrueOnlyForMayachain() {
        val mayachainAddress = AccountType.MayachainAddress("maya1watched")

        assertTrue(BlockchainType.Mayachain.supports(mayachainAddress))
        assertFalse(BlockchainType.Thorchain.supports(mayachainAddress))
        assertFalse(BlockchainType.Stellar.supports(mayachainAddress))
    }

    @Test
    fun eligibleTokens_hardwareCardAccountOnThorchain_returnsEmpty() {
        val fullCoin = FullCoin(
            coin = Coin(uid = "rune", name = "THORChain", code = "RUNE"),
            tokens = listOf(nativeToken(BlockchainType.Thorchain)),
        )
        val hardwareCard = AccountType.HardwareCard("card-id", 0, "public-key", 0)

        assertTrue(fullCoin.eligibleTokens(hardwareCard).isEmpty())
    }

    @Test
    fun eligibleTokens_mnemonicAccountOnThorchain_containsToken() {
        val fullCoin = FullCoin(
            coin = Coin(uid = "rune", name = "THORChain", code = "RUNE"),
            tokens = listOf(nativeToken(BlockchainType.Thorchain)),
        )
        val mnemonic = AccountType.Mnemonic(listOf("word"), "")

        assertTrue(fullCoin.eligibleTokens(mnemonic).isNotEmpty())
    }

    @Test
    fun supported_containsThorchainAndMayachain() {
        assertTrue(BlockchainType.supported.contains(BlockchainType.Thorchain))
        assertTrue(BlockchainType.supported.contains(BlockchainType.Mayachain))
    }
}
