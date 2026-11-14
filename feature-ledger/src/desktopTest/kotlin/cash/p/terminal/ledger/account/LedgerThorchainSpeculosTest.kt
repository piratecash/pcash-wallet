package cash.p.terminal.ledger.account

import cash.p.terminal.ledger.Speculos
import cash.p.terminal.ledger.account.AccountFixtures.THORCHAIN_ADDRESS
import cash.p.terminal.ledger.account.AccountFixtures.THORCHAIN_PUBLIC_KEY
import cash.p.terminal.ledger.account.AccountSpeculos.fetchStoredKey
import cash.p.terminal.ledger.account.AccountSpeculos.signingKey
import cash.p.terminal.ledger.account.AccountSpeculos.verifyAddress
import cash.p.terminal.ledgerkit.LedgerSession
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.hdwalletkit.ECKey
import io.horizontalsystems.thorchainkit.models.AccountInfo
import io.horizontalsystems.thorchainkit.models.Address
import io.horizontalsystems.thorchainkit.models.Asset
import io.horizontalsystems.thorchainkit.network.Network
import io.horizontalsystems.thorchainkit.network.ThornodeApiProvider
import io.horizontalsystems.thorchainkit.transaction.TransactionSender
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import java.math.BigInteger
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Needs speculos with the THORChain app; see [Speculos]. Transactions go through thorchain-kit's external signer path;
 * each signature is checked against the seed's key over a sign doc written out here, independently of the kit.
 */
class LedgerThorchainSpeculosTest {

    private val broadcast = slot<ByteArray>()
    private val provider = mockk<ThornodeApiProvider> {
        coEvery { fetchAccount(any()) } returns AccountInfo(ACCOUNT_NUMBER, SEQUENCE)
        coEvery { broadcast(capture(broadcast)) } returns "HASH"
    }
    private val sender = TransactionSender(Address.fromString(THORCHAIN_ADDRESS, Network.Mainnet), Network.Mainnet, provider)

    @Test
    fun fetchKey_thorchainApp_matchesSeedKeyAndAddress() = withThorchain { session ->
        val stored = fetchStoredKey(session, rune)

        assertEquals(THORCHAIN_ADDRESS, stored.key.value)
        assertEquals(THORCHAIN_PUBLIC_KEY, stored.publicKey.toHexString())
        assertEquals(THORCHAIN_ADDRESS, Address.fromPublicKey(stored.publicKey, Network.Mainnet).toString())
    }

    @Test
    fun verify_thorchainAddress_isConfirmedOnDevice() = withThorchain { verifyAddress(it, rune) }

    // The offline path: signed for later broadcast.
    @Test
    fun signSend_rune_signedBySeedKeyOverAminoDoc() = withThorchain { session ->
        val signer = LedgerThorchainSigner(signingKey(session, rune))
        val recipient = Address.fromString(RECIPIENT, Network.Mainnet)

        val signed = Speculos.approving { sender.signSend(recipient, BigInteger.valueOf(150_000_000), "rune", MEMO, signer) }

        assertSignedBySeed(signed.raw, SEND_DOC)
    }

    @Test
    fun deposit_swapMemo_signedBySeedKeyOverAminoDoc() = withThorchain { session ->
        val signer = LedgerThorchainSigner(signingKey(session, rune))

        Speculos.approving { sender.deposit(Asset.Rune, BigInteger.valueOf(100_000_000), SWAP_MEMO, signer) }

        assertSignedBySeed(broadcast.captured, DEPOSIT_DOC)
    }

    // TxRaw ends with its only signature, field 3 of 64 bytes.
    private fun assertSignedBySeed(txRaw: ByteArray, doc: String) {
        val signature = txRaw.copyOfRange(txRaw.size - SIGNATURE_SIZE, txRaw.size)
        val hash = MessageDigest.getInstance("SHA-256").digest(doc.encodeToByteArray())

        assertEquals(SIGNATURE_TAG, txRaw.copyOfRange(txRaw.size - SIGNATURE_SIZE - 2, txRaw.size - SIGNATURE_SIZE).toHexString())
        assertTrue(
            (0..1).any { id ->
                ECKey.recoverPublicKeyFromSignature(hash, signature, id)?.toHexString() == THORCHAIN_PUBLIC_KEY
            }
        )
    }

    private fun withThorchain(block: suspend (LedgerSession) -> Unit) =
        runBlocking { Speculos.withSession("THORChain", block) }

    private companion object {
        const val SIGNATURE_SIZE = 64
        const val SIGNATURE_TAG = "1a40"
        const val ACCOUNT_NUMBER = 123L
        const val SEQUENCE = 456L
        const val RECIPIENT = "thor1mwyrp6lj85swy5e5g4hjlaacm33g6rw3p4qmq4"
        const val MEMO = "ledger test"
        const val SWAP_MEMO = "=:BTC.BTC:bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq:0/1/0"
        val rune = TokenQuery(BlockchainType.Thorchain, TokenType.Native)

        // Sorted keys, no whitespace, as thornode verifies SIGN_MODE_LEGACY_AMINO_JSON.
        const val SEND_DOC =
            """{"account_number":"123","chain_id":"thorchain-1","fee":{"amount":[],"gas":"6000000"},""" +
                """"memo":"ledger test","msgs":[{"type":"thorchain/MsgSend","value":{"amount":[{"amount":""" +
                """"150000000","denom":"rune"}],"from_address":"$THORCHAIN_ADDRESS","to_address":"$RECIPIENT"}}],""" +
                """"sequence":"456"}"""
        const val DEPOSIT_DOC =
            """{"account_number":"123","chain_id":"thorchain-1","fee":{"amount":[],"gas":"600000000"},"memo":"",""" +
                """"msgs":[{"type":"thorchain/MsgDeposit","value":{"coins":[{"amount":"100000000","asset":""" +
                """"THOR.RUNE"}],"memo":"$SWAP_MEMO","signer":"$THORCHAIN_ADDRESS"}}],"sequence":"456"}"""
    }
}
