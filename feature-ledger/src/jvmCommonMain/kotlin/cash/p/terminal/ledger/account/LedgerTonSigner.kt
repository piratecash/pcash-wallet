package cash.p.terminal.ledger.account

import cash.p.terminal.ledger.domain.LedgerSigningKey
import cash.p.terminal.ledgerkit.ton.TonAddress
import cash.p.terminal.ledgerkit.ton.TonAppClient
import cash.p.terminal.ledgerkit.ton.TonBody
import cash.p.terminal.ledgerkit.ton.TonBodyHint
import cash.p.terminal.ledgerkit.ton.TonCellRef
import cash.p.terminal.ledgerkit.ton.TonCoins
import cash.p.terminal.ledgerkit.ton.TonTransfer
import com.tonapps.ledger.ton.TonPayloadFormat
import com.tonapps.ledger.ton.Transaction
import io.horizontalsystems.tonkit.core.TonDeviceSignature
import io.horizontalsystems.tonkit.core.TonTransactionDeviceSigner
import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.block.StateInit
import org.ton.cell.Cell

/** Signs v4R2 transfers on the TON app; never touches the network. ton-kit checks the signed hash. */
class LedgerTonSigner(private val key: LedgerSigningKey) : TonTransactionDeviceSigner {

    override suspend fun sign(transaction: Transaction): TonDeviceSignature {
        val signature = key.sign { transport, path ->
            TonAppClient(transport).signTransaction(path, transaction.toTonTransfer())
        }
        return TonDeviceSignature(signature.signature, signature.hash)
    }
}

internal fun Transaction.toTonTransfer() = TonTransfer(
    seqno = seqno.toUInt(),
    // A deploy transfer carries all ones, which the device takes as an unsigned value.
    timeout = timeout.toUInt(),
    destination = destination.toTonAddress(),
    value = coins.toTonCoins(),
    bounce = bounceable,
    sendMode = sendMode,
    stateInit = stateInit?.let { StateInit.createCell(it).toCellRef() },
    body = body?.let { TonBody(it.toCellRef(), payload?.toHint()) },
)

// An NFT transfer has no hint in the app, so it goes blind like any unrecognised body.
private fun TonPayloadFormat.toHint(): TonBodyHint? = when (this) {
    is TonPayloadFormat.Comment -> TonBodyHint.Comment(text)
    is TonPayloadFormat.JettonTransfer -> queryId?.let { id ->
        TonBodyHint.JettonTransfer(
            queryId = id.toLong().toULong(),
            amount = coins.toTonCoins(),
            destination = receiverAddress.toTonAddress(),
            responseDestination = excessesAddress.toTonAddress(),
            customPayload = customPayload?.toCellRef(),
            forwardAmount = forwardAmount.toTonCoins(),
            forwardPayload = forwardPayload?.toCellRef(),
        )
    }
    is TonPayloadFormat.NftTransfer -> null
}

private fun AddrStd.toTonAddress() = TonAddress(workchainId, address.toByteArray())

private fun Coins.toTonCoins() = TonCoins(amount.value.toByteArray())

private fun Cell.toCellRef() = TonCellRef(hash().toByteArray(), depth())
