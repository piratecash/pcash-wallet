package cash.p.terminal.ledger.bitcoin

import cash.p.terminal.ledger.bitcoin.BtcTxFixtures.CHANGE_ADDRESS
import cash.p.terminal.ledger.bitcoin.BtcTxFixtures.RECIPIENT_ADDRESS
import cash.p.terminal.ledger.bitcoin.BtcTxFixtures.funding
import cash.p.terminal.ledger.bitcoin.BtcTxFixtures.key
import cash.p.terminal.ledger.bitcoin.BtcTxFixtures.lockingScript
import cash.p.terminal.ledger.bitcoin.BtcTxFixtures.mutableTransaction
import cash.p.terminal.ledger.bitcoin.BtcTxFixtures.output
import cash.p.terminal.ledger.bitcoin.BtcTxFixtures.spec
import cash.p.terminal.ledger.bitcoin.BtcTxFixtures.spend
import cash.p.terminal.ledger.domain.LedgerSigningFailedException
import cash.p.terminal.ledgerkit.bitcoin.LegacySigningMode
import cash.p.terminal.ledgerkit.bitcoin.PartialSignature
import cash.p.terminal.ledgerkit.bitcoin.Psbt
import io.horizontalsystems.bitcoincore.extensions.hexToByteArray
import io.horizontalsystems.bitcoincore.extensions.toReversedHex
import io.horizontalsystems.bitcoincore.storage.FullTransaction
import io.horizontalsystems.bitcoincore.transactions.scripts.OpCodes
import io.horizontalsystems.bitcoincore.transactions.scripts.ScriptType
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LedgerBtcRequestsTest {

    private val inputKey = key(KEY_A, external = true, index = 3)
    private val changeKey = key(KEY_B, external = false, index = 1)

    @Test
    fun toPsbt_nativeSegwitInputWithChange_carriesUtxosAndDerivations() {
        val previous = funding(ScriptType.P2WPKH, inputKey, 60_000)
        val transaction = segwitSpend(previous, ScriptType.P2WPKH)

        val psbt = transaction.toPsbt(LedgerBtcAccount(FINGERPRINT, spec(BtcTxFixtures.bitcoinBip84)), previousOf(previous))

        val input = psbt.inputs.single()
        assertContentEquals(previous.header.hash, input.previousTxid)
        assertContentEquals(BtcTxFixtures.serializer.serialize(previous), input.nonWitnessUtxo)
        assertEquals(60_000, input.witnessUtxo?.amount)
        assertContentEquals(lockingScript(ScriptType.P2WPKH, inputKey), input.witnessUtxo?.script)
        assertNull(input.redeemScript)
        assertEquals("84'/0'/0'/0/3", input.bip32Derivation?.path.toString())
        assertContentEquals(FINGERPRINT, input.bip32Derivation?.masterFingerprint)
        assertContentEquals(inputKey.publicKey, input.bip32Derivation?.publicKey)
        assertNull(input.tapBip32Derivation)
        assertNull(psbt.outputs[0].bip32Derivation)
        assertEquals("84'/0'/0'/1/1", psbt.outputs[1].bip32Derivation?.path.toString())
    }

    @Test
    fun toPsbt_nestedSegwitInput_carriesWitnessProgramAsRedeemScript() {
        val previous = funding(ScriptType.P2WPKHSH, inputKey, 60_000)
        val transaction = segwitSpend(previous, ScriptType.P2WPKHSH)

        val psbt = transaction.toPsbt(LedgerBtcAccount(FINGERPRINT, spec(BtcTxFixtures.bitcoinBip49)), previousOf(previous))

        val witnessProgram = OpCodes.scriptWPKH(inputKey.publicKeyHash)
        assertContentEquals(witnessProgram, psbt.inputs.single().redeemScript)
        assertEquals("49'/0'/0'/0/3", psbt.inputs.single().bip32Derivation?.path.toString())
        assertContentEquals(OpCodes.scriptWPKH(changeKey.publicKeyHash), psbt.outputs[1].redeemScript)
    }

    @Test
    fun toPsbt_taprootInput_usesXOnlyKeyWithoutPreviousTransaction() {
        val previous = funding(ScriptType.P2TR, inputKey, 60_000)
        val transaction = segwitSpend(previous, ScriptType.P2TR)

        val psbt = transaction.toPsbt(LedgerBtcAccount(FINGERPRINT, spec(BtcTxFixtures.bitcoinBip86)), emptyMap())

        val input = psbt.inputs.single()
        assertNull(input.nonWitnessUtxo)
        assertNull(input.bip32Derivation)
        assertContentEquals(inputKey.publicKey.copyOfRange(1, 33), input.tapBip32Derivation?.publicKey)
        assertEquals("86'/0'/0'/0/3", input.tapBip32Derivation?.path.toString())
        assertEquals("86'/0'/0'/1/1", psbt.outputs[1].tapBip32Derivation?.path.toString())
    }

    @Test
    fun toLegacyTransaction_litecoinSegwit_usesSegwitModeAndChangePath() {
        val previous = funding(ScriptType.P2WPKH, inputKey, 60_000)
        val transaction = segwitSpend(previous, ScriptType.P2WPKH)

        val legacy = transaction.toLegacyTransaction(
            LedgerBtcApp.Litecoin,
            spec(BtcTxFixtures.litecoinBip84),
            previousOf(previous),
        )

        assertEquals(LegacySigningMode.SEGWIT, legacy.mode)
        assertEquals("84'/2'/0'/1/1", legacy.changePath.toString())
        val input = legacy.inputs.single()
        assertEquals("84'/2'/0'/0/3", input.path.toString())
        assertContentEquals(BtcTxFixtures.serializer.serialize(previous), input.previousTransaction)
        assertEquals(0xFFFFFFFDL, input.sequence)
        assertEquals(listOf(50_000L, 9_000L), legacy.outputs.map { it.amount })
    }

    @Test
    fun toLegacyTransaction_bitcoinCash_usesForkIdMode() {
        val previous = funding(ScriptType.P2PKH, inputKey, 60_000)
        val transaction = mutableTransaction(
            inputs = listOf(spend(previous, ScriptType.P2PKH, inputKey)),
            outputs = listOf(output(59_000, lockingScript(ScriptType.P2PKH, changeKey), RECIPIENT_ADDRESS)),
        )

        val legacy = transaction.toLegacyTransaction(
            LedgerBtcApp.BitcoinCash,
            spec(BtcTxFixtures.bitcoinCash),
            previousOf(previous),
        )

        assertEquals(LegacySigningMode.BITCOIN_CASH, legacy.mode)
        assertEquals("44'/145'/0'/0/3", legacy.inputs.single().path.toString())
        assertNull(legacy.changePath)
    }

    @Test
    fun inInputOrder_signaturesOutOfOrder_returnsThemByInput() {
        val first = funding(ScriptType.P2WPKH, inputKey, 60_000)
        val secondKey = key(KEY_B, external = true, index = 4)
        val second = funding(ScriptType.P2WPKH, secondKey, 70_000)
        val transaction = mutableTransaction(
            inputs = listOf(spend(first, ScriptType.P2WPKH, inputKey), spend(second, ScriptType.P2WPKH, secondKey)),
            outputs = listOf(output(120_000, lockingScript(ScriptType.P2WPKH, changeKey), RECIPIENT_ADDRESS)),
        )
        val psbt = transaction.toPsbt(
            LedgerBtcAccount(FINGERPRINT, spec(BtcTxFixtures.bitcoinBip84)),
            previousOf(first) + previousOf(second),
        )

        val signatures = listOf(
            PartialSignature(1, secondKey.publicKey, byteArrayOf(2)),
            PartialSignature(0, inputKey.publicKey, byteArrayOf(1)),
        ).inInputOrder(psbt)

        assertEquals(listOf(1.toByte(), 2.toByte()), signatures.map { it.single() })
    }

    @Test
    fun inInputOrder_signatureFromAnotherKey_throwsSigningFailed() {
        val previous = funding(ScriptType.P2WPKH, inputKey, 60_000)
        val psbt = segwitSpend(previous, ScriptType.P2WPKH)
            .toPsbt(LedgerBtcAccount(FINGERPRINT, spec(BtcTxFixtures.bitcoinBip84)), previousOf(previous))

        assertFailsWith<LedgerSigningFailedException> {
            listOf(PartialSignature(0, changeKey.publicKey, byteArrayOf(1))).inInputOrder(psbt)
        }
    }

    @Test
    fun inInputOrder_taprootSignatureWithTweakedOutputKey_returnsIt() {
        val psbt = taprootVectorPsbt()

        val signatures = listOf(PartialSignature(0, TR_OUTPUT_KEY.hexToByteArray(), byteArrayOf(1))).inInputOrder(psbt)

        assertEquals(1.toByte(), signatures.single().single())
    }

    @Test
    fun inInputOrder_taprootSignatureWithInternalKey_throwsSigningFailed() {
        val psbt = taprootVectorPsbt()

        assertFailsWith<LedgerSigningFailedException> {
            listOf(PartialSignature(0, TR_INTERNAL_KEY.hexToByteArray(), byteArrayOf(1))).inInputOrder(psbt)
        }
    }

    @Test
    fun applySignatures_eachInputType_placesSignatureLikeTheKit() {
        val types = listOf(ScriptType.P2PKH, ScriptType.P2WPKH, ScriptType.P2WPKHSH, ScriptType.P2TR)
        val transaction = mutableTransaction(
            inputs = types.map { spend(funding(it, inputKey, 10_000), it, inputKey) },
            outputs = listOf(output(30_000, lockingScript(ScriptType.P2WPKH, changeKey), RECIPIENT_ADDRESS)),
        )
        val signatures = types.indices.map { byteArrayOf(0x30, it.toByte()) }

        transaction.applySignatures(signatures)

        val (p2pkh, p2wpkh, nested, taproot) = transaction.inputsToSign.map { it.input }
        assertContentEquals(OpCodes.push(signatures[0]) + OpCodes.push(inputKey.publicKey), p2pkh.sigScript)
        assertTrue(p2pkh.witness.isEmpty())
        assertEquals(listOf(signatures[1], inputKey.publicKey).map { it.toList() }, p2wpkh.witness.map { it.toList() })
        assertTrue(p2wpkh.sigScript.isEmpty())
        assertContentEquals(OpCodes.push(OpCodes.scriptWPKH(inputKey.publicKeyHash)), nested.sigScript)
        assertEquals(listOf(signatures[2], inputKey.publicKey).map { it.toList() }, nested.witness.map { it.toList() })
        assertEquals(listOf(signatures[3].toList()), taproot.witness.map { it.toList() })
        assertTrue(transaction.transaction.segwit)
    }

    @Test
    fun applySignatures_legacyInputsOnly_keepsTransactionNonSegwit() {
        val transaction = mutableTransaction(
            inputs = listOf(spend(funding(ScriptType.P2PKH, inputKey, 10_000), ScriptType.P2PKH, inputKey)),
            outputs = listOf(output(9_000, lockingScript(ScriptType.P2PKH, changeKey), RECIPIENT_ADDRESS)),
        )

        transaction.applySignatures(listOf(byteArrayOf(0x30)))

        assertFalse(transaction.transaction.segwit)
    }

    @Test
    fun requireSupportedInputs_scriptHashInput_throwsSigningFailed() {
        val transaction = mutableTransaction(
            inputs = listOf(spend(funding(ScriptType.P2SH, inputKey, 10_000), ScriptType.P2SH, inputKey)),
            outputs = listOf(output(9_000, lockingScript(ScriptType.P2PKH, changeKey), RECIPIENT_ADDRESS)),
        )

        assertFailsWith<LedgerSigningFailedException> { transaction.requireSupportedInputs() }
    }

    private fun segwitSpend(previous: FullTransaction, type: ScriptType) =
        mutableTransaction(
            inputs = listOf(spend(previous, type, inputKey)),
            outputs = listOf(
                output(50_000, lockingScript(ScriptType.P2WPKH, key(KEY_C)), RECIPIENT_ADDRESS),
                output(9_000, lockingScript(type, changeKey), CHANGE_ADDRESS),
            ),
            changeKey = changeKey,
        )

    // Keys of ledger-kit's tr1to2 sign-psbt vector: the device answers with the tweaked output key.
    private fun taprootVectorPsbt(): Psbt {
        val internalKey = key("02$TR_INTERNAL_KEY", external = true, index = 3)
        val previous = funding(ScriptType.P2TR, internalKey, 60_000)
        return mutableTransaction(
            inputs = listOf(spend(previous, ScriptType.P2TR, internalKey)),
            outputs = listOf(output(50_000, lockingScript(ScriptType.P2WPKH, key(KEY_C)), RECIPIENT_ADDRESS)),
        ).toPsbt(LedgerBtcAccount(FINGERPRINT, spec(BtcTxFixtures.bitcoinBip86)), emptyMap())
    }

    private fun previousOf(transaction: FullTransaction) =
        mapOf(transaction.header.hash.toReversedHex() to BtcTxFixtures.serializer.serialize(transaction))

    private companion object {
        val FINGERPRINT = "f5acc2fd".hexToByteArray()
        const val KEY_A = "031869567d5e88d988ff7baf6827983f89530ddd79dbaeadaa6ec538a8f03dea8b"
        const val KEY_B = "03a975f065a0f5ecfe849ba3d31d7fdadf4fdbea233ffc095057e9977f54f237d1"
        const val KEY_C = "02f9308a019258c31049344f85f89d5229b531c845836f99b08601f113bce036f9"
        const val TR_INTERNAL_KEY = "e9c69b095ed04b562afc8d41691936ba573eb6f689bc236600be4fa3926f6412"
        const val TR_OUTPUT_KEY = "89904a6348d7ff9a44d49db8b6a807c622e868364c547da426f30b5a6213186a"
    }
}
