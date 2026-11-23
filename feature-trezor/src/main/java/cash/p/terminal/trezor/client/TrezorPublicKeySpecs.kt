package cash.p.terminal.trezor.client

import cash.p.terminal.trezor.domain.TrezorModelSupport
import cash.p.terminal.trezor.domain.model.TrezorModel
import cash.p.terminal.trezorkit.client.TrezorInputScriptType
import cash.p.terminal.trezorkit.client.TrezorKeyResult
import cash.p.terminal.trezorkit.client.TrezorPublicKeyRequest
import cash.p.terminal.wallet.entities.HardwarePublicKey
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.hardware.HardwareKeyCoin
import cash.p.terminal.wallet.hardware.HardwareKeySpec
import cash.p.terminal.wallet.hardware.HardwarePublicKeySpecs
import cash.p.terminal.wallet.hardware.HardwareVendor
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.hdwalletkit.HDWallet

/**
 * Maps app [TokenQuery]s to the neutral [TrezorPublicKeyRequest]s the kit understands and turns the
 * kit's [TrezorKeyResult]s back into [HardwarePublicKey]s, on top of the vendor-neutral
 * [HardwarePublicKeySpecs], so create-wallet, scan-to-add, and UI eligibility all agree.
 */
object TrezorPublicKeySpecs {

    /** A neutral [spec] paired with the Trezor request that derives it. */
    data class QuerySpec(
        val spec: HardwareKeySpec,
        val request: TrezorPublicKeyRequest,
    ) {
        val query: TokenQuery get() = spec.query
        val derivationPath: String get() = spec.derivationPath
        val expectedPurpose: HDWallet.Purpose? get() = spec.expectedPurpose
    }

    /** Builds a [QuerySpec] per supported query, skipping pairs Trezor cannot derive here. */
    fun buildQuerySpecs(tokenQueries: List<TokenQuery>): List<QuerySpec> =
        tokenQueries.mapNotNull { specFor(it) }

    val walletIdentityTokenQuery = HardwarePublicKeySpecs.walletIdentityTokenQuery

    val walletIdentityRequest: TrezorPublicKeyRequest by lazy {
        requireNotNull(specFor(walletIdentityTokenQuery)).request
    }

    /** Single source of truth for UI eligibility: can Trezor derive this token variant at all. */
    fun supports(model: TrezorModel?, blockchainType: BlockchainType, tokenType: TokenType): Boolean =
        TrezorModelSupport.isSupported(model, blockchainType) &&
            specFor(TokenQuery(blockchainType, tokenType)) != null

    fun toHardwarePublicKey(
        spec: QuerySpec,
        result: TrezorKeyResult,
        accountId: String,
    ): HardwarePublicKey = HardwarePublicKeySpecs.toHardwarePublicKey(
        spec = spec.spec,
        key = result.key,
        publicKey = result.publicKey,
        accountId = accountId,
    )

    private fun specFor(query: TokenQuery): QuerySpec? =
        HardwarePublicKeySpecs.specFor(query, HardwareVendor.Trezor)?.let { QuerySpec(it, request(it)) }

    private fun request(spec: HardwareKeySpec): TrezorPublicKeyRequest {
        val path = TrezorDerivationPath.parse(spec.derivationPath)
        return when (spec.coin) {
            HardwareKeyCoin.Ethereum -> TrezorPublicKeyRequest.Ethereum(path)
            HardwareKeyCoin.Solana -> TrezorPublicKeyRequest.Solana(path)
            HardwareKeyCoin.Stellar -> TrezorPublicKeyRequest.Stellar(path)
            HardwareKeyCoin.Tron -> TrezorPublicKeyRequest.Tron(path)
            else -> TrezorPublicKeyRequest.Bitcoin(
                spec.coin.trezorCoinName,
                path,
                requireNotNull(spec.expectedPurpose).scriptType,
            )
        }
    }

    // "Bcash" is Trezor's upstream coin_name for Bitcoin Cash (matches the BCH signer).
    private val HardwareKeyCoin.trezorCoinName: String
        get() = if (this == HardwareKeyCoin.BitcoinCash) "Bcash" else name

    private val HDWallet.Purpose.scriptType: TrezorInputScriptType
        get() = when (this) {
            HDWallet.Purpose.BIP44 -> TrezorInputScriptType.SPENDADDRESS
            HDWallet.Purpose.BIP49 -> TrezorInputScriptType.SPENDP2SHWITNESS
            HDWallet.Purpose.BIP84 -> TrezorInputScriptType.SPENDWITNESS
            HDWallet.Purpose.BIP86 -> TrezorInputScriptType.SPENDTAPROOT
        }
}
