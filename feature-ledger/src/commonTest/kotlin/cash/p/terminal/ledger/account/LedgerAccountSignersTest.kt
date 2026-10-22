package cash.p.terminal.ledger.account

import cash.p.terminal.ledger.account.AccountFixtures.SOLANA_ADDRESS
import cash.p.terminal.ledger.account.AccountFixtures.SOLANA_PUBLIC_KEY
import cash.p.terminal.ledger.account.AccountFixtures.SOL_TRANSFER
import cash.p.terminal.ledger.account.AccountFixtures.SOL_TRANSFER_SIGNATURE
import cash.p.terminal.ledger.account.AccountFixtures.STELLAR_ACCOUNT_ID
import cash.p.terminal.ledger.account.AccountFixtures.STELLAR_PAYMENT
import cash.p.terminal.ledger.account.AccountFixtures.STELLAR_PAYMENT_SIGNATURE
import cash.p.terminal.ledger.account.AccountFixtures.TRON_ADDRESS
import cash.p.terminal.ledger.account.AccountFixtures.TRON_PUBLIC_KEY
import cash.p.terminal.ledger.account.AccountFixtures.TRX_TRANSFER
import cash.p.terminal.ledger.account.AccountFixtures.TRX_TRANSFER_SIGNATURE
import cash.p.terminal.ledger.account.AccountFixtures.signingKey
import cash.p.terminal.ledger.account.AccountFixtures.storedKey
import cash.p.terminal.ledger.domain.LedgerSigningFailedException
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.hdwalletkit.ECKey
import io.horizontalsystems.tronkit.network.CreatedTransaction
import io.horizontalsystems.tronkit.network.RawData
import kotlinx.coroutines.test.runTest
import org.stellar.sdk.KeyPair
import org.stellar.sdk.Network
import org.stellar.sdk.Transaction
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LedgerAccountSignersTest {

    @Test
    fun sign_solanaMessage_sendsMessageAtLedgerPathAndReturnsDeviceSignature() {
        val device = FakeAccountApp(mapOf(SOLANA_SIGN to SOL_TRANSFER_SIGNATURE.hexToByteArray()))
        val signer = LedgerSolanaSigner(signingKey(device, storedKey(solana, SOLANA_PUBLIC_KEY)))

        val signature = signer.sign(SOL_TRANSFER.hexToByteArray())

        assertEquals(SOL_TRANSFER_SIGNATURE, signature.toHexString())
        assertEquals(SOLANA_ADDRESS, signer.publicKey.toBase58())
        assertEquals(SIGNER_COUNT + SOLANA_PATH + SOL_TRANSFER, device.data().toHexString())
    }

    @Test
    fun sign_tronTransaction_returnsSignatureWithRecoveryIdOfStoredKey() {
        val device = FakeAccountApp(mapOf(TRON_SIGN to TRX_TRANSFER_SIGNATURE.hexToByteArray()))
        val stored = storedKey(tron, TRON_ADDRESS, TRON_PUBLIC_KEY.hexToByteArray())

        val signature = LedgerTronSigner(signingKey(device, stored)).sign(createdTransaction(TRX_TRANSFER))

        assertEquals(TRX_TRANSFER_SIGNATURE, signature.toHexString())
        assertEquals(TRON_PATH + TRX_TRANSFER, device.data().toHexString())
    }

    @Test
    fun sign_tronSignatureOfAnotherAccount_throwsSigningFailed() {
        val device = FakeAccountApp(mapOf(TRON_SIGN to TRX_TRANSFER_SIGNATURE.hexToByteArray()))
        val otherKey = ECKey.fromPrivate(ByteArray(32) { 7 }, compressed = false).pubKey
        val signer = LedgerTronSigner(signingKey(device, storedKey(tron, TRON_ADDRESS, otherKey)))

        assertFailsWith<LedgerSigningFailedException> { signer.sign(createdTransaction(TRX_TRANSFER)) }
    }

    // stellar_signXDR and stellar_signAndSubmitXDR hand the kit an envelope it decodes into this transaction.
    @Test
    fun signTransaction_stellarEnvelope_sendsSignatureBaseAndDecoratesDeviceSignature() = runTest {
        val device = FakeAccountApp(mapOf(STELLAR_SIGN to STELLAR_PAYMENT_SIGNATURE.hexToByteArray()))
        val signer = LedgerStellarSigner(signingKey(device, storedKey(stellar, STELLAR_ACCOUNT_ID)))
        val transaction = Transaction.fromEnvelopeXdr(envelopeOf(STELLAR_PAYMENT), Network.PUBLIC) as Transaction

        val signature = signer.signTransaction(transaction)

        assertEquals(STELLAR_PATH + STELLAR_PAYMENT, device.data().toHexString())
        val keyPair = KeyPair.fromAccountId(STELLAR_ACCOUNT_ID)
        assertContentEquals(keyPair.signatureHint.signatureHint, signature.hint.signatureHint)
        assertTrue(keyPair.verify(transaction.hash(), signature.signature.signature))
    }

    @Test
    fun sign_stellarBareHash_throwsWithoutTouchingDevice() = runTest {
        val device = FakeAccountApp()
        val signer = LedgerStellarSigner(signingKey(device, storedKey(stellar, STELLAR_ACCOUNT_ID)))

        assertFailsWith<LedgerSigningFailedException> { signer.sign(ByteArray(32)) }
        assertTrue(device.apdus.isEmpty())
    }

    private fun createdTransaction(rawDataHex: String) = CreatedTransaction(
        visible = false,
        txID = "",
        raw_data = RawData(emptyList(), "", "", 0, 0, null),
        raw_data_hex = rawDataHex,
        Error = null,
    )

    // A v1 envelope is the signature base without the network id, followed by an empty signature list.
    private fun envelopeOf(signatureBase: String): String =
        Base64.getEncoder().encodeToString((signatureBase.drop(NETWORK_ID_HEX_LENGTH) + NO_SIGNATURES).hexToByteArray())

    private companion object {
        const val SOLANA_SIGN = 0x06
        const val TRON_SIGN = 0x04
        const val STELLAR_SIGN = 0x04
        const val SIGNER_COUNT = "01"
        const val SOLANA_PATH = "038000002c800001f580000000"
        const val TRON_PATH = "058000002c800000c3800000000000000000000000"
        const val STELLAR_PATH = "038000002c8000009480000000"
        const val NETWORK_ID_HEX_LENGTH = 64
        const val NO_SIGNATURES = "00000000"
        val solana = TokenQuery(BlockchainType.Solana, TokenType.Native)
        val tron = TokenQuery(BlockchainType.Tron, TokenType.Native)
        val stellar = TokenQuery(BlockchainType.Stellar, TokenType.Native)
    }
}
