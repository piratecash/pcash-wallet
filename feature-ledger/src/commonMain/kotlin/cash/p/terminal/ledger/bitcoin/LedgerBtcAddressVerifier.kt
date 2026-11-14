package cash.p.terminal.ledger.bitcoin

import cash.p.terminal.ledger.domain.LedgerAddressVerifier
import cash.p.terminal.ledgerkit.bitcoin.BitcoinAppClient
import cash.p.terminal.ledgerkit.bitcoin.BtchipLegacyClient
import cash.p.terminal.ledgerkit.transport.LedgerTransport
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.hardware.HardwareAddressLocator

/** Shows a wallet's own address on the device; the user compares it with the one on the phone. */
class LedgerBtcAddressVerifier(private val adapterManager: IAdapterManager) : LedgerAddressVerifier {

    override fun supports(query: TokenQuery): Boolean = LedgerBtcApp.specFor(query) != null

    override suspend fun verify(transport: LedgerTransport, wallet: Wallet, address: String) {
        val (spec, app) = checkNotNull(LedgerBtcApp.specFor(wallet.token.tokenQuery))
        val position = checkNotNull(
            (adapterManager.getReceiveAdapterForWallet(wallet) as? HardwareAddressLocator)?.locate(address)
        ) { "The address does not belong to this wallet" }
        if (app.usesPsbt) {
            val client = BitcoinAppClient(transport)
            val policy = LedgerBtcAccount(client.getMasterFingerprint(), spec)
                .policy(checkNotNull(wallet.hardwarePublicKey).key.value)
            client.getWalletAddress(policy, position.change, position.index, display = true)
        } else {
            BtchipLegacyClient(transport).getWalletPublicKey(
                path = ledgerAddressPath(spec.derivationPath, position),
                format = app.addressFormat(checkNotNull(spec.expectedPurpose)),
                display = true,
            )
        }
    }
}
