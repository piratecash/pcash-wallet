package cash.p.terminal.ledger.zcash

import cash.p.terminal.ledger.bitcoin.ledgerAddressPath
import cash.p.terminal.ledger.domain.LedgerAddressVerifier
import cash.p.terminal.ledger.domain.LedgerAppKeyFetcher
import cash.p.terminal.ledger.domain.LedgerAppRequirements
import cash.p.terminal.ledger.domain.LedgerIdentityMismatchException
import cash.p.terminal.ledger.domain.LedgerKey
import cash.p.terminal.ledger.domain.ledgerPath
import cash.p.terminal.ledgerkit.bitcoin.BtchipLegacyClient
import cash.p.terminal.ledgerkit.transport.LedgerTransport
import cash.p.terminal.ledgerkit.zcash.ZcashAppClient
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.hardware.HardwareAddressLocator
import cash.p.terminal.wallet.hardware.HardwareKeyCoin
import cash.p.terminal.wallet.hardware.HardwareKeySpec
import cash.p.terminal.wallet.hardware.HardwarePublicKeySpecs
import cash.p.terminal.wallet.hardware.HardwareVendor

/** The device's first ZIP-32/BIP-44 account, the only one the wallet uses. */
const val LEDGER_ZCASH_ACCOUNT = 0

internal fun ledgerZcashSpec(query: TokenQuery): HardwareKeySpec? =
    HardwarePublicKeySpecs.specFor(query, HardwareVendor.Ledger)?.takeIf { it.coin == HardwareKeyCoin.Zcash }

/**
 * Stores the unified viewing key, which the app shows for approval, with the transparent account key, which it
 * gives without asking: that key identifies the device before every signature.
 */
class LedgerZcashKeyFetcher : LedgerAppKeyFetcher {
    override val appName: String = LedgerAppRequirements.ZCASH_APP

    override fun supports(query: TokenQuery): Boolean = ledgerZcashSpec(query) != null

    override suspend fun fetchKey(transport: LedgerTransport, query: TokenQuery): LedgerKey =
        fetchKeys(transport, listOf(query)).single()

    override suspend fun fetchKeys(transport: LedgerTransport, queries: List<TokenQuery>): List<LedgerKey> {
        val specs = queries.map { checkNotNull(ledgerZcashSpec(it)) }
        val accountKey = BtchipLegacyClient(transport).getWalletPublicKey(specs.first().ledgerPath()).publicKey
        val viewingKey = ZcashAppClient(transport).getUnifiedFullViewingKey(LEDGER_ZCASH_ACCOUNT)
        return specs.map { LedgerKey(it, viewingKey, accountKey) }
    }

    override suspend fun holdsKey(
        transport: LedgerTransport,
        query: TokenQuery,
        key: String,
        publicKey: ByteArray,
    ): Boolean {
        val path = checkNotNull(ledgerZcashSpec(query)).ledgerPath()
        return BtchipLegacyClient(transport).getWalletPublicKey(path).publicKey.contentEquals(publicKey)
    }
}

/** Shows the receive address on the device; the app can show only its Orchard-only unified address. */
class LedgerZcashAddressVerifier(private val adapterManager: IAdapterManager) : LedgerAddressVerifier {

    override fun supports(query: TokenQuery): Boolean = ledgerZcashSpec(query) != null

    override suspend fun verify(transport: LedgerTransport, wallet: Wallet, address: String) {
        val spec = checkNotNull(ledgerZcashSpec(wallet.token.tokenQuery))
        val shown = if (spec.query.tokenType == UNIFIED) {
            ZcashAppClient(transport).getUnifiedAddress(LEDGER_ZCASH_ACCOUNT, display = true)
        } else {
            val position = checkNotNull(
                (adapterManager.getReceiveAdapterForWallet(wallet) as? HardwareAddressLocator)?.locate(address)
            ) { "The address does not belong to this wallet" }
            val path = ledgerAddressPath(spec.derivationPath, position)
            BtchipLegacyClient(transport).getWalletPublicKey(path, display = true).address
        }
        if (shown != address) throw LedgerIdentityMismatchException()
    }

    private companion object {
        val UNIFIED = TokenType.AddressSpecTyped(TokenType.AddressSpecType.Unified)
    }
}
