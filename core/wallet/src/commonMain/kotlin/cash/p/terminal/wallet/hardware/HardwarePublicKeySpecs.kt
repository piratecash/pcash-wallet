package cash.p.terminal.wallet.hardware

import cash.p.terminal.wallet.accountTypeDerivation
import cash.p.terminal.wallet.entities.HardwarePublicKey
import cash.p.terminal.wallet.entities.HardwarePublicKeyType
import cash.p.terminal.wallet.entities.SecretString
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.entities.TonWalletVersion
import cash.p.terminal.wallet.entities.tonAsset
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.hdwalletkit.HDExtendedKey
import io.horizontalsystems.hdwalletkit.HDWallet

enum class HardwareVendor { Trezor, Ledger }

/** The device-side app or coin a key is derived by; vendors map it to their own request. */
enum class HardwareKeyCoin {
    Bitcoin, Litecoin, BitcoinCash, Dogecoin, Dash, Zcash, Ethereum, Solana, Stellar, Tron, Ton, Thorchain, Monero
}

/** One [query] with the coin and BIP-32 path to derive, plus the purpose a BTC-like xpub must match. */
data class HardwareKeySpec(
    val query: TokenQuery,
    val coin: HardwareKeyCoin,
    val derivationPath: String,
    val expectedPurpose: HDWallet.Purpose?,
)

class HardwareKeyValidationException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** The single place where a `(blockchainType, tokenType)` pair becomes a hardware derivation path. */
object HardwarePublicKeySpecs {

    val walletIdentityTokenQuery =
        TokenQuery(BlockchainType.Bitcoin, TokenType.Derived(TokenType.Derivation.Bip84))

    fun specFor(query: TokenQuery, vendor: HardwareVendor): HardwareKeySpec? = when (query.blockchainType) {
        BlockchainType.Bitcoin,
        BlockchainType.Litecoin,
        BlockchainType.BitcoinCash,
        BlockchainType.Dogecoin,
        BlockchainType.Dash -> bitcoinFamily(query)
        BlockchainType.Ethereum,
        BlockchainType.BinanceSmartChain,
        BlockchainType.Polygon,
        BlockchainType.ArbitrumOne,
        BlockchainType.Optimism,
        BlockchainType.Base,
        BlockchainType.RobinhoodChain -> evmSpec(query)
        // Trezor keeps the EVM chains it was released with.
        BlockchainType.Avalanche,
        BlockchainType.Gnosis,
        BlockchainType.Fantom,
        BlockchainType.ZkSync -> evmSpec(query).takeIf { vendor == HardwareVendor.Ledger }
        BlockchainType.Solana -> fixedPath(query, HardwareKeyCoin.Solana, solanaPath(vendor))
        BlockchainType.Stellar -> fixedPath(query, HardwareKeyCoin.Stellar, "m/44'/148'/0'")
        BlockchainType.Tron -> fixedPath(query, HardwareKeyCoin.Tron, "m/44'/195'/0'/0/0")
        BlockchainType.Zcash -> zcash(query, vendor)
        // Trezor has no TON or THORChain app; the kit's external signer drives only the v4R2 contract.
        BlockchainType.Ton -> fixedPath(query, HardwareKeyCoin.Ton, "m/44'/607'/0'/0'/0'/0'")
            .takeIf { vendor == HardwareVendor.Ledger && query.tonAsset?.version != TonWalletVersion.W5 }
        BlockchainType.Thorchain ->
            fixedPath(query, HardwareKeyCoin.Thorchain, "m/44'/931'/0'/0/0").takeIf { vendor == HardwareVendor.Ledger }
        // Trezor keeps Monero in its own cold-signing wallet, outside the public key store.
        BlockchainType.Monero -> fixedPath(query, HardwareKeyCoin.Monero, "m/44'/$MONERO_COIN_TYPE'/0'")
            .takeIf { vendor == HardwareVendor.Ledger && query.tokenType == TokenType.Native }
        else -> null
    }

    fun toHardwarePublicKey(
        spec: HardwareKeySpec,
        key: String,
        publicKey: ByteArray,
        accountId: String,
    ): HardwarePublicKey {
        spec.expectedPurpose?.let { validateXpubPurpose(key, it) }
        return HardwarePublicKey(
            accountId = accountId,
            blockchainType = spec.query.blockchainType.uid,
            type = HardwarePublicKeyType.PUBLIC_KEY,
            tokenType = spec.query.tokenType,
            key = SecretString(key),
            derivationPath = spec.derivationPath,
            publicKey = publicKey,
            derivedPublicKey = publicKey,
        )
    }

    // Ledger's Solana app derives the account key one level higher than Trezor.
    private fun solanaPath(vendor: HardwareVendor): String = when (vendor) {
        HardwareVendor.Trezor -> "m/44'/501'/0'/0'"
        HardwareVendor.Ledger -> "m/44'/501'/0'"
    }

    private fun bitcoinFamily(query: TokenQuery): HardwareKeySpec? = when (query.blockchainType) {
        BlockchainType.Bitcoin ->
            bitcoinDerived(query, HardwareKeyCoin.Bitcoin, BITCOIN_COIN_TYPE, BITCOIN_PURPOSES)
        BlockchainType.Litecoin ->
            bitcoinDerived(query, HardwareKeyCoin.Litecoin, LITECOIN_COIN_TYPE, LITECOIN_PURPOSES)
        BlockchainType.BitcoinCash -> bitcoinCash(query)
        BlockchainType.Dogecoin -> bitcoinNative(query, HardwareKeyCoin.Dogecoin, DOGECOIN_COIN_TYPE)
        BlockchainType.Dash -> bitcoinNative(query, HardwareKeyCoin.Dash, DASH_COIN_TYPE)
        else -> null
    }

    private fun zcash(query: TokenQuery, vendor: HardwareVendor): HardwareKeySpec? = when (vendor) {
        HardwareVendor.Trezor -> zcashTransparent(query)
        HardwareVendor.Ledger -> zcashLedger(query)
    }

    /** ZEC — only the transparent address spec is derivable; shielded/unified have no BIP-32 path. */
    private fun zcashTransparent(query: TokenQuery): HardwareKeySpec? {
        if ((query.tokenType as? TokenType.AddressSpecTyped)?.type != TokenType.AddressSpecType.Transparent) {
            return null
        }
        return bitcoinSpec(query, HardwareKeyCoin.Zcash, ZCASH_COIN_TYPE, HDWallet.Purpose.BIP44)
    }

    /** Ledger holds a viewing key of transparent + Orchard under the BIP-44 account; its app has no Sapling. */
    private fun zcashLedger(query: TokenQuery): HardwareKeySpec? {
        val spec = (query.tokenType as? TokenType.AddressSpecTyped)?.type
        if (spec != TokenType.AddressSpecType.Transparent && spec != TokenType.AddressSpecType.Unified) return null
        return fixedPath(query, HardwareKeyCoin.Zcash, "m/44'/$ZCASH_COIN_TYPE'/0'")
    }

    /** BTC/LTC — the derivation is chosen by the user via `TokenType.Derived`. */
    private fun bitcoinDerived(
        query: TokenQuery,
        coin: HardwareKeyCoin,
        coinType: Int,
        allowed: Set<HDWallet.Purpose>,
    ): HardwareKeySpec? {
        val purpose = (query.tokenType as? TokenType.Derived)?.derivation?.accountTypeDerivation?.purpose
            ?: return null
        if (purpose !in allowed) return null
        return bitcoinSpec(query, coin, coinType, purpose)
    }

    /** BCH — no derivation choice, but two address types map to different coin types. */
    private fun bitcoinCash(query: TokenQuery): HardwareKeySpec? {
        val coinType = when ((query.tokenType as? TokenType.AddressTyped)?.type) {
            TokenType.AddressType.Type0 -> BITCOIN_COIN_TYPE
            TokenType.AddressType.Type145 -> BITCOIN_CASH_COIN_TYPE
            null -> return null
        }
        return bitcoinSpec(query, HardwareKeyCoin.BitcoinCash, coinType, HDWallet.Purpose.BIP44)
    }

    /** DOGE/DASH — single legacy (BIP44) derivation, carried by `TokenType.Native`. */
    private fun bitcoinNative(query: TokenQuery, coin: HardwareKeyCoin, coinType: Int): HardwareKeySpec? {
        if (query.tokenType != TokenType.Native) return null
        return bitcoinSpec(query, coin, coinType, HDWallet.Purpose.BIP44)
    }

    private fun bitcoinSpec(
        query: TokenQuery,
        coin: HardwareKeyCoin,
        coinType: Int,
        purpose: HDWallet.Purpose,
    ) = HardwareKeySpec(query, coin, "m/${purpose.value}'/$coinType'/0'", purpose)

    private fun evmSpec(query: TokenQuery) = fixedPath(query, HardwareKeyCoin.Ethereum, "m/44'/60'/0'/0/0")

    private fun fixedPath(query: TokenQuery, coin: HardwareKeyCoin, path: String) =
        HardwareKeySpec(query, coin, path, null)

    /**
     * Fail-loud: the version of the returned xpub must be compatible with the requested purpose.
     * Distinguishes every device-level mismatch except BIP44 vs BIP86 (both serialize as `xpub`),
     * which is instead guaranteed by the deterministic purpose→path mapping (unit-tested).
     */
    private fun validateXpubPurpose(xpub: String, expected: HDWallet.Purpose) {
        val purposes = xpubPurposes(xpub)
        if (expected !in purposes) {
            throw HardwareKeyValidationException(
                "Hardware key purpose mismatch: expected $expected but the key permits $purposes"
            )
        }
    }

    private fun xpubPurposes(xpub: String): List<HDWallet.Purpose> =
        try {
            HDExtendedKey(xpub).purposes
        } catch (e: HDExtendedKey.ParsingError) {
            throw unparseableKey(e)
        } catch (e: RuntimeException) {
            throw unparseableKey(e)
        }

    private fun unparseableKey(cause: Throwable) =
        HardwareKeyValidationException("Hardware wallet returned an unparseable extended public key", cause)

    // SLIP-44 registered coin types.
    private const val BITCOIN_COIN_TYPE = 0
    private const val LITECOIN_COIN_TYPE = 2
    private const val DOGECOIN_COIN_TYPE = 3
    private const val DASH_COIN_TYPE = 5
    private const val MONERO_COIN_TYPE = 128
    private const val ZCASH_COIN_TYPE = 133
    private const val BITCOIN_CASH_COIN_TYPE = 145

    private val BITCOIN_PURPOSES = setOf(
        HDWallet.Purpose.BIP44,
        HDWallet.Purpose.BIP49,
        HDWallet.Purpose.BIP84,
        HDWallet.Purpose.BIP86,
    )
    private val LITECOIN_PURPOSES = setOf(
        HDWallet.Purpose.BIP44,
        HDWallet.Purpose.BIP49,
        HDWallet.Purpose.BIP84,
    )
}
