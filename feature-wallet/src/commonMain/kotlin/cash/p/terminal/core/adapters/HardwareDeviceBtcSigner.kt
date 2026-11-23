package cash.p.terminal.core.adapters

import cash.p.terminal.wallet.hardware.BtcPreviousTransactionProvider
import io.horizontalsystems.bitcoincore.AbstractKit
import io.horizontalsystems.bitcoincore.transactions.builder.IInputSigner
import io.horizontalsystems.bitcoincore.transactions.builder.ISchnorrInputSigner

/** A USB-device signer of either vendor, built before its kit and bound to it once the kit exists. */
class HardwareDeviceBtcSigner(
    override val inputSigner: IInputSigner,
    override val schnorrInputSigner: ISchnorrInputSigner,
    private val previousTransactions: KitBtcPreviousTransactionProvider,
) : BitcoinDeviceSigner {
    override fun bindKit(kit: AbstractKit) = previousTransactions.bind(kit)
}

/** Previous transactions from the wallet's own kit storage, so device signing stays offline-safe. */
class KitBtcPreviousTransactionProvider : BtcPreviousTransactionProvider {
    @Volatile
    private var kit: AbstractKit? = null

    fun bind(kit: AbstractKit) {
        this.kit = kit
    }

    override fun getRawTransaction(hash: String): String? =
        checkNotNull(kit) { "The kit is not bound yet" }.getRawTransaction(hash)
}
