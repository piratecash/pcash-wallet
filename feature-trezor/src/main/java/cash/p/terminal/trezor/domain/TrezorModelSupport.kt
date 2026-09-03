package cash.p.terminal.trezor.domain

import cash.p.terminal.trezor.domain.model.TrezorModel
import cash.p.terminal.trezorkit.client.TrezorFeatures
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType

object TrezorModelSupport {

    private val universalBlockchains = setOf(
        BlockchainType.Bitcoin,
        BlockchainType.Litecoin,
        BlockchainType.BitcoinCash,
        BlockchainType.Dogecoin,
        BlockchainType.Zcash,
        BlockchainType.Ethereum,
        BlockchainType.BinanceSmartChain,
        BlockchainType.Polygon,
        BlockchainType.ArbitrumOne,
        BlockchainType.Optimism,
        BlockchainType.Base,
        BlockchainType.Stellar,
    )

    fun getSupportedBlockchains(model: TrezorModel?): Set<BlockchainType> {
        if (model == null) return universalBlockchains

        return buildSet {
            addAll(universalBlockchains)
            when (model) {
                TrezorModel.One -> add(BlockchainType.Dash)
                TrezorModel.ModelT -> {
                    add(BlockchainType.Dash)
                    add(BlockchainType.Solana)
                    add(BlockchainType.Tron)
                }
                TrezorModel.Safe3,
                TrezorModel.Safe5,
                TrezorModel.Safe7 -> {
                    add(BlockchainType.Solana)
                    add(BlockchainType.Tron)
                }
            }
            if (model == TrezorModel.Safe5) {
                add(BlockchainType.Monero)
            }
        }
    }

    fun isSupported(model: TrezorModel?, blockchainType: BlockchainType): Boolean =
        blockchainType in getSupportedBlockchains(model)

    /** Trezor derives only the transparent address; the default Zcash query must agree with [BlockchainType.Zcash]'s
     *  [TrezorPublicKeySpecs][cash.p.terminal.trezor.client.TrezorPublicKeySpecs] spec or no Zcash wallet is created. */
    private val zcashTransparentQuery =
        TokenQuery(BlockchainType.Zcash, TokenType.AddressSpecTyped(TokenType.AddressSpecType.Transparent))

    fun getDefaultTokenQueries(model: TrezorModel?): List<TokenQuery> {
        val supported = getSupportedBlockchains(model)
        return TokenQuery.defaultTokenQueries
            .filter { it.blockchainType in supported && it.blockchainType != BlockchainType.Monero }
            .map { if (it == TokenQuery.ZcashUnified) zcashTransparentQuery else it }
    }

    /**
     * Drops token queries the connected device cannot derive on its current firmware. Model support
     * advertises Tron for every Safe/Model T, but Tron signing landed only in core firmware 2.11.0;
     * on older firmware a TronGetAddress is rejected and fails the whole derivation batch, so Tron
     * must be removed unless the device reports [TrezorFeatures.supportsTron]. Zcash is gated the
     * same way by [TrezorFeatures.supportsZcash].
     */
    fun filterByFirmwareCapabilities(
        tokenQueries: List<TokenQuery>,
        features: TrezorFeatures
    ): List<TokenQuery> = tokenQueries.filterNot {
        (it.blockchainType == BlockchainType.Tron && !features.supportsTron) ||
            (it.blockchainType == BlockchainType.Zcash && !features.supportsZcash)
    }
}
