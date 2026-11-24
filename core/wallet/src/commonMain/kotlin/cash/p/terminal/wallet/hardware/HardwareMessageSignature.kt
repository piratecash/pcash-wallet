package cash.p.terminal.wallet.hardware

import io.horizontalsystems.bitcoincore.messages.StandardMessage
import io.horizontalsystems.bitcoincore.models.PublicKey
import io.horizontalsystems.bitcoincore.transactions.scripts.ScriptType
import io.horizontalsystems.hdwalletkit.ECKey

private const val DEVICE_SIGNATURE_SIZE = 65

/**
 * A device's `header || r || s` message signature re-headed for [scriptType]. Recovering [publicKey] from it
 * proves the device signed with the requested address's key; any other key throws.
 */
fun standardMessageSignature(
    deviceSignature: ByteArray,
    message: String,
    publicKey: PublicKey,
    scriptType: ScriptType,
): ByteArray {
    require(deviceSignature.size == DEVICE_SIGNATURE_SIZE) { "Unexpected signature size ${deviceSignature.size}" }
    val compactSignature = deviceSignature.copyOfRange(1, DEVICE_SIGNATURE_SIZE)
    return StandardMessage.signature(ECKey.messageHash(message), compactSignature, publicKey.publicKey, scriptType)
}
