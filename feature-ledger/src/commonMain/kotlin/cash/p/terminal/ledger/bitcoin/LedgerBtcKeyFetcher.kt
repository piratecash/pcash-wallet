package cash.p.terminal.ledger.bitcoin

import cash.p.terminal.ledger.domain.LedgerAppKeyFetcher
import cash.p.terminal.ledger.domain.LedgerKey
import cash.p.terminal.ledgerkit.DerivationPath
import cash.p.terminal.ledgerkit.bitcoin.BitcoinAppClient
import cash.p.terminal.ledgerkit.bitcoin.BtchipLegacyClient
import cash.p.terminal.ledgerkit.transport.LedgerTransport
import cash.p.terminal.wallet.entities.TokenQuery
import io.horizontalsystems.hdwalletkit.HDExtendedKey
import io.horizontalsystems.hdwalletkit.HDKey

/** Reads account extended keys in the format Trezor stores, so the watch and sync path is vendor-neutral. */
class LedgerBtcKeyFetcher(private val app: LedgerBtcApp) : LedgerAppKeyFetcher {
    override val appName: String = app.appName

    override fun supports(query: TokenQuery): Boolean = LedgerBtcApp.specFor(query)?.second == app

    override suspend fun fetchKey(transport: LedgerTransport, query: TokenQuery): LedgerKey {
        val spec = checkNotNull(LedgerBtcApp.specFor(query)).first
        val path = DerivationPath.parse(spec.derivationPath)
        val accountKey = if (app.usesPsbt) {
            HDExtendedKey(BitcoinAppClient(transport).getExtendedPubkey(path)).key
        } else {
            legacyAccountKey(BtchipLegacyClient(transport), path)
        }
        val key = accountKey.serializePublic(app.keyVersion(checkNotNull(spec.expectedPurpose)).value)
        return LedgerKey(spec, key, accountKey.pubKey)
    }

    // The legacy apps return no xpub: assemble it from the account key and its parent's fingerprint.
    private suspend fun legacyAccountKey(client: BtchipLegacyClient, path: DerivationPath): HDKey {
        val parent = client.getWalletPublicKey(DerivationPath(path.indices.dropLast(1)))
        val account = client.getWalletPublicKey(path)
        val parentFingerprint = HDKey(parent.publicKey, parent.chainCode, null, 0, 0, 0, false).getFingerprint()
        return HDKey(
            pubKey = account.publicKey,
            chainCode = account.chainCode,
            parent = null,
            parentFingerprint = parentFingerprint,
            depth = path.indices.size,
            childNumber = (path.indices.last() and CHILD_NUMBER_MASK).toInt(),
            isHardened = true,
        )
    }

    private companion object {
        const val CHILD_NUMBER_MASK = 0x7FFFFFFFu
    }
}
