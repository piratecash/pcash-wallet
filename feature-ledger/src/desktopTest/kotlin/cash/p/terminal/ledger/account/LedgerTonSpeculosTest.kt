package cash.p.terminal.ledger.account

import cash.p.terminal.ledger.Speculos
import cash.p.terminal.ledger.account.AccountFixtures.TON_PUBLIC_KEY
import cash.p.terminal.ledger.account.AccountFixtures.TON_RAW_ADDRESS
import cash.p.terminal.ledger.account.AccountSpeculos.fetchStoredKey
import cash.p.terminal.ledger.account.AccountSpeculos.signingKey
import cash.p.terminal.ledger.account.AccountSpeculos.verifyAddress
import cash.p.terminal.ledger.domain.LedgerBlindSigningRequiredException
import cash.p.terminal.ledgerkit.LedgerSession
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import com.tonapps.blockchain.ton.TonTransferHelper
import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.wallet.data.core.entity.SendRequestEntity
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.tonkit.Address
import io.horizontalsystems.tonkit.api.TonApi
import io.horizontalsystems.tonkit.core.TonExternalSignerException
import io.horizontalsystems.tonkit.core.TonWallet
import io.horizontalsystems.tonkit.core.TransactionSigner
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.io.bytestring.ByteString
import org.json.JSONArray
import org.json.JSONObject
import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.block.Message
import org.ton.boc.BagOfCells
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.kotlin.crypto.PublicKeyEd25519
import org.ton.tlb.constructor.AnyTlbConstructor
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Needs speculos with the TON app; see [Speculos]. Each transfer goes through ton-kit's swap signing path (what
 * BaseTonAdapter.sendWithPayloadBoc uses); the broadcast message is checked against the seed's key independently.
 */
class LedgerTonSpeculosTest {

    private val publicKey = PublicKeyEd25519(ByteString(*TON_PUBLIC_KEY.hexToByteArray()))
    private val recipient = AddrStd.parse("0:" + "2f".repeat(32))
    private val own = AddrStd.parse(TON_RAW_ADDRESS)
    private val api = mockk<TonApi> {
        coEvery { getAccountSeqno(any<String>()) } returns SEQNO
    }

    @Test
    fun fetchKey_tonApp_matchesSeedKeyAndV4R2Address() = withTon { session ->
        val key = fetchStoredKey(session, ton).key.value

        assertEquals(TON_PUBLIC_KEY, key)
        assertEquals(Address.parse(TON_RAW_ADDRESS), TonWallet.ExternalSigner(publicKey, mockk()).address)
    }

    @Test
    fun verify_tonAddress_isConfirmedOnDevice() = withTon { verifyAddress(it, ton) }

    @Test
    fun sign_commentTransfer_broadcastsMessageSignedBySeedKey() = withTon { session ->
        val payload = cell { storeUInt(0, 32); storeBytes("ledger test".encodeToByteArray()) }

        val boc = Speculos.approving { sign(session, request(message(recipient, payload))) }

        assertSignedBySeed(boc, payload)
    }

    @Test
    fun sign_jettonTransfer_broadcastsMessageSignedBySeedKey() = withTon { session ->
        val forward = cell { storeUInt(0, 32); storeBytes("hi".encodeToByteArray()) }
        val payload = TonTransferHelper.jetton(Coins.ofNano(1_000_000), recipient, own, BigInteger.TEN, 1L, forward)
        val jettonWallet = AddrStd.parse("0:" + "3c".repeat(32))

        val boc = Speculos.approving { sign(session, request(message(jettonWallet, payload))) }

        assertSignedBySeed(boc, payload)
    }

    @Test
    fun sign_swapPayload_needsBlindSigning() = withTon { session ->
        // A router call the app cannot show, like a STON.fi swap.
        val payload = cell { storeUInt(0x12345678, 32); storeUInt(42, 64) }
        val request = request(message(recipient, payload))
        Speculos.chooseInFirstSetting(BLIND_OFF)

        val error = assertFailsWith<LedgerBlindSigningRequiredException> { sign(session, request) }
        assertEquals("TON", error.appName)
        Speculos.dismiss("Blind signing must be")

        Speculos.chooseInFirstSetting(BLIND_ON)
        val boc = try {
            Speculos.approving { sign(session, request) }
        } finally {
            Speculos.chooseInFirstSetting(BLIND_OFF)
        }
        assertSignedBySeed(boc, payload)
    }

    @Test
    fun sign_twoMessages_refusedBeforeDevice() = withTon { session ->
        val payload = cell { storeUInt(0, 32) }

        assertFailsWith<TonExternalSignerException.UnsupportedMessageCount> {
            sign(session, request(message(recipient, payload), message(recipient, payload)))
        }
    }

    private suspend fun sign(session: LedgerSession, request: SendRequestEntity): String {
        val wallet = TonWallet.ExternalSigner(publicKey, LedgerTonSigner(signingKey(session, ton)))
        return TransactionSigner(api).sign(request, wallet)
    }

    /** Parses the external message as the network does: the seed's key signed the rest of its body. */
    private fun assertSignedBySeed(boc: String, payload: Cell) {
        val message = Message.tlbCodec(AnyTlbConstructor).loadTlb(BagOfCells(base64(boc)).first())
        val body = requireNotNull(message.body.x ?: message.body.y?.value)
        val bits = body.beginParse()
        val signature = bits.loadByteArray(SIGNATURE_SIZE)
        // Bit by bit: ton-kotlin 0.5.0 bulk loadBits after a read overruns its buffer.
        val unsigned = cell {
            repeat(bits.remainingBits) { storeBit(bits.loadBit()) }
            storeRefs(body.refs)
        }

        assertTrue(publicKey.verifySignature(unsigned.hash().toByteArray(), signature))
        assertEquals(payload.hash(), unsigned.refs.single().refs.last().hash())
    }

    private fun message(destination: AddrStd, payload: Cell) = JSONObject()
        .put("address", destination.toString(userFriendly = false))
        .put("amount", "10000000")
        .put("payload", payload.base64())

    private fun request(vararg messages: JSONObject) =
        SendRequestEntity(JSONObject().put("messages", JSONArray(messages.toList())), "id", "p.cash")

    private fun cell(build: CellBuilder.() -> Unit): Cell = CellBuilder.beginCell().apply(build).endCell()

    private fun withTon(block: suspend (LedgerSession) -> Unit) = runBlocking { Speculos.withSession("TON", block) }

    private companion object {
        const val SEQNO = 5
        const val SIGNATURE_SIZE = 64
        const val BLIND_OFF = 0
        const val BLIND_ON = 1
        val ton = TokenQuery(BlockchainType.Ton, TokenType.Native)
    }
}
