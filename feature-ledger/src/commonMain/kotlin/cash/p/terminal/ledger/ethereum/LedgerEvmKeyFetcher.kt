package cash.p.terminal.ledger.ethereum

import cash.p.terminal.ledger.domain.LedgerAppKeyFetcher
import cash.p.terminal.ledger.domain.LedgerKey
import cash.p.terminal.ledger.domain.ledgerPath
import cash.p.terminal.ledgerkit.ethereum.EthereumAppClient
import cash.p.terminal.ledgerkit.transport.LedgerTransport
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.hardware.HardwareKeyCoin
import cash.p.terminal.wallet.hardware.HardwareKeySpec
import cash.p.terminal.wallet.hardware.HardwarePublicKeySpecs
import cash.p.terminal.wallet.hardware.HardwareVendor
import io.horizontalsystems.hdwalletkit.ECKey
import io.horizontalsystems.hdwalletkit.HDExtendedKeyVersion
import io.horizontalsystems.hdwalletkit.HDKey

internal const val ETHEREUM_APP = "Ethereum"

/** One Ethereum app key serves every EVM chain and token. */
internal fun ledgerEvmSpec(query: TokenQuery): HardwareKeySpec? =
    HardwarePublicKeySpecs.specFor(query, HardwareVendor.Ledger)?.takeIf { it.coin == HardwareKeyCoin.Ethereum }

/** Stores the address key as an xpub, like Trezor, so EVM address resolution stays vendor-neutral. */
class LedgerEvmKeyFetcher : LedgerAppKeyFetcher {
    override val appName: String = ETHEREUM_APP

    override fun supports(query: TokenQuery): Boolean = ledgerEvmSpec(query) != null

    override suspend fun fetchKey(transport: LedgerTransport, query: TokenQuery): LedgerKey {
        val spec = checkNotNull(ledgerEvmSpec(query))
        val address = EthereumAppClient(transport).getAddress(spec.ledgerPath())
        val publicKey = ECKey(address.publicKey).pubKeyCompressed
        return LedgerKey(spec, addressXpub(publicKey, spec), publicKey)
    }

    // The app gives no chain code; nothing derives below an address key, so a zero one is enough.
    private fun addressXpub(publicKey: ByteArray, spec: HardwareKeySpec): String = HDKey(
        pubKey = publicKey,
        chainCode = ByteArray(CHAIN_CODE_SIZE),
        parent = null,
        parentFingerprint = 0,
        depth = spec.ledgerPath().indices.size,
        childNumber = 0,
        isHardened = false,
    ).serializePublic(HDExtendedKeyVersion.xpub.value)

    private companion object {
        const val CHAIN_CODE_SIZE = 32
    }
}
