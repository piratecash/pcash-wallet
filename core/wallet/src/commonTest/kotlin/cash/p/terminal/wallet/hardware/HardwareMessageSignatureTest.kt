package cash.p.terminal.wallet.hardware

import io.horizontalsystems.bitcoincore.messages.MessageSignError
import io.horizontalsystems.bitcoincore.models.PublicKey
import io.horizontalsystems.bitcoincore.transactions.scripts.ScriptType
import io.horizontalsystems.hdwalletkit.ECKey
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HardwareMessageSignatureTest {

    private val key = ECKey.fromPrivate(ByteArray(32) { 1 })
    private val publicKey = PublicKey().apply { publicKey = key.pubKey }

    @Test
    fun standardMessageSignature_eachScriptType_setsBip137HeaderAndKeepsRs() {
        val deviceSignature = Base64.getDecoder().decode(key.signMessage(MESSAGE))
        val recoveryId = (deviceSignature[0].toInt() and 0xFF) - COMPRESSED_P2PKH_BASE

        mapOf(ScriptType.P2PKH to 31, ScriptType.P2WPKHSH to 35, ScriptType.P2WPKH to 39).forEach { (scriptType, base) ->
            val signature = standardMessageSignature(deviceSignature, MESSAGE, publicKey, scriptType)

            assertEquals(base + recoveryId, signature[0].toInt() and 0xFF, scriptType.name)
            assertContentEquals(deviceSignature.copyOfRange(1, 65), signature.copyOfRange(1, 65))
        }
    }

    @Test
    fun standardMessageSignature_signedByOtherKey_throwsSignatureMismatch() {
        val otherSignature = Base64.getDecoder().decode(ECKey.fromPrivate(ByteArray(32) { 2 }).signMessage(MESSAGE))

        assertFailsWith<MessageSignError.SignatureMismatch> {
            standardMessageSignature(otherSignature, MESSAGE, publicKey, ScriptType.P2WPKH)
        }
    }

    @Test
    fun standardMessageSignature_wrongSize_throws() {
        assertFailsWith<IllegalArgumentException> {
            standardMessageSignature(ByteArray(64), MESSAGE, publicKey, ScriptType.P2WPKH)
        }
    }

    private companion object {
        const val MESSAGE = "Ich bestätige\n"
        const val COMPRESSED_P2PKH_BASE = 31
    }
}
