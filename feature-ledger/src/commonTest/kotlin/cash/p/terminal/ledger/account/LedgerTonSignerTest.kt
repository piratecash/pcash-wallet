package cash.p.terminal.ledger.account

import cash.p.terminal.ledger.account.AccountFixtures.TON_PUBLIC_KEY
import cash.p.terminal.ledger.account.AccountFixtures.TON_RAW_ADDRESS
import cash.p.terminal.ledger.account.AccountFixtures.account
import cash.p.terminal.ledger.account.AccountFixtures.signingKey
import cash.p.terminal.ledger.account.AccountFixtures.storedKey
import cash.p.terminal.ledger.domain.LedgerAppAccess
import cash.p.terminal.ledger.domain.LedgerIdentityMismatchException
import cash.p.terminal.ledger.domain.LedgerSigningKey
import cash.p.terminal.ledgerkit.apdu.LedgerException
import cash.p.terminal.ledgerkit.transport.LedgerTransport
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import com.tonapps.ledger.ton.TonPayloadFormat
import com.tonapps.ledger.ton.Transaction
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.ton.bigint.BigInt
import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.block.MsgAddressInt
import org.ton.block.StateInit
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Requests, hashes and signatures: LedgerHQ/app-ton test client vectors checked on speculos (ledger-kit). */
class LedgerTonSignerTest {

    private val ton = TokenQuery(BlockchainType.Ton, TokenType.Native)
    private val destination = AddrStd.parse("0:" + "2f".repeat(32))
    private val jettonWallet = AddrStd.parse("0:" + "3c".repeat(32))
    private val own = AddrStd.parse(TON_RAW_ADDRESS)
    private val forwardHi = cell { storeUInt(0, 32); storeBytes("hi".encodeToByteArray()) }
    private val max120 = Coins(BigInt(BigInteger.ONE.shiftLeft(120).subtract(BigInteger.ONE).toString()))

    @Test
    fun sign_comment_sendsCommentHintAndReturnsDeviceSignature() = runTest {
        val device = deviceSigning(COMMENT_HASH)
        val transaction = Transaction(
            destination = destination, sendMode = 3, seqno = 7, timeout = TIMEOUT, bounceable = false,
            coins = Coins.ofNano(1_500_000_000), payload = TonPayloadFormat.Comment("ledger-kit test"),
            body = cell { storeUInt(0, 32); storeBytes("ledger-kit test".encodeToByteArray()) },
        )

        val signature = signer(device).sign(transaction)

        assertEquals(COMMENT_REQUEST, device.request())
        assertEquals(SIGNATURE, signature.signature.toHexString())
        assertEquals(COMMENT_HASH, signature.signedHash.toHexString())
    }

    @Test
    fun sign_jettonTransfer_sendsJettonHint() = runTest {
        val device = deviceSigning(JETTON_HASH)
        val transaction = Transaction(
            destination = jettonWallet, sendMode = 3, seqno = 8, timeout = TIMEOUT, bounceable = true,
            coins = Coins.ofNano(50_000_000),
            payload = jettonHint(BigInteger.valueOf(12345), Coins.ofNano(1_000_000), null, Coins.ofNano(1), forwardHi),
            body = jettonBody(BigInteger.valueOf(12345), Coins.ofNano(1_000_000), null, Coins.ofNano(1), forwardHi),
        )

        signer(device).sign(transaction)

        assertEquals(JETTON_REQUEST, device.request())
    }

    @Test
    fun sign_stateInitAndMaxValues_mapsEveryField() = runTest {
        val device = deviceSigning(JETTON_BIG_HASH)
        val custom = cell { storeUInt(0xC057, 16) }
        val maxQueryId = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE)
        val transaction = Transaction(
            destination = jettonWallet, sendMode = 1, seqno = 11, timeout = TIMEOUT, bounceable = true,
            coins = max120,
            stateInit = StateInit(cell { storeUInt(0xC0DE, 16) }, cell { storeUInt(0xDA7A, 16) }),
            payload = jettonHint(maxQueryId, max120, custom, max120, forwardHi),
            body = jettonBody(maxQueryId, max120, custom, max120, forwardHi),
        )

        signer(device).sign(transaction)

        assertEquals(JETTON_BIG_REQUEST, device.request())
    }

    @Test
    fun sign_unrecognizedBodyOrNft_sendsNoHint() = runTest {
        val body = cell { storeUInt(0x12345678, 32); storeUInt(42, 64) }
        val nft = TonPayloadFormat.NftTransfer(null, destination, own, null, Coins.ofNano(0), null)
        listOf(null, nft).forEach { payload ->
            val device = deviceSigning(UNSAFE_HASH)
            val transaction = Transaction(
                destination = destination, sendMode = 1, seqno = 9, timeout = TIMEOUT, bounceable = true,
                coins = Coins.ofNano(10_000_000), payload = payload, body = body,
            )

            signer(device).sign(transaction)

            assertEquals(UNSAFE_REQUEST, device.request())
        }
    }

    @Test
    fun sign_deployTransfer_sendsAllOnesTimeout() = runTest {
        val device = deviceSigning(UNSAFE_HASH)
        val transaction = Transaction(
            destination = destination, sendMode = 3, seqno = 0, timeout = -1, bounceable = true,
            coins = Coins.ofNano(1),
        )

        signer(device).sign(transaction)

        // Tag byte, then seqno and timeout as big-endian uint32.
        assertTrue(device.request().startsWith("00" + "00000000" + "ffffffff"))
    }

    @Test
    fun sign_otherDeviceConnected_neverSendsTransaction() = runTest {
        val device = deviceSigning(COMMENT_HASH)
        val access = mockk<LedgerAppAccess> {
            coEvery { signWithLedgerApp<Any>(any(), any(), any()) } throws LedgerIdentityMismatchException()
        }
        val key = LedgerSigningKey(access, account, storedKey(ton, TON_PUBLIC_KEY))

        assertFailsWith<LedgerIdentityMismatchException> { LedgerTonSigner(key).sign(plainTransfer()) }
        assertTrue(device.apdus.isEmpty())
    }

    @Test
    fun sign_deviceAnswers_mapsRejectionAndBlindSigning() = runTest {
        assertFailsWith<LedgerException.UserRejected> { signer(failingDevice(0x6985)).sign(plainTransfer()) }
        assertFailsWith<LedgerException.BlindSigningRequired> { signer(failingDevice(0xBD00)).sign(plainTransfer()) }
        assertFailsWith<LedgerException.DataTooLarge> { signer(failingDevice(0xB004)).sign(plainTransfer()) }
    }

    private fun signer(device: LedgerTransport) = LedgerTonSigner(signingKey(device, storedKey(ton, TON_PUBLIC_KEY)))

    private fun deviceSigning(hash: String) =
        FakeAccountApp(
            mapOf(SIGN_TX to byteArrayOf(64) + SIGNATURE.hexToByteArray() + byteArrayOf(32) + hash.hexToByteArray()),
            isLastChunk = { it[P2].toInt() == 0 },
        )

    private fun failingDevice(statusWord: Int) = object : LedgerTransport {
        override suspend fun exchange(apdu: ByteArray) =
            byteArrayOf((statusWord shr 8).toByte(), statusWord.toByte())

        override fun close() = Unit
    }

    // The first APDU carries the path; the transfer request follows in the others.
    private fun FakeAccountApp.request(): String =
        apdus.drop(1).joinToString("") { it.copyOfRange(APDU_HEADER, it.size).toHexString() }

    private fun plainTransfer() = Transaction(
        destination = destination, sendMode = 1, seqno = 1, timeout = TIMEOUT, bounceable = true,
        coins = Coins.ofNano(1),
    )

    private fun jettonHint(queryId: BigInteger, amount: Coins, custom: Cell?, forwardAmount: Coins, forward: Cell?) =
        TonPayloadFormat.JettonTransfer(queryId, amount, destination, own, custom, forwardAmount, forward)

    private fun jettonBody(queryId: BigInteger, amount: Coins, custom: Cell?, forwardAmount: Coins, forward: Cell?) =
        cell {
            storeUInt(0x0f8a7ea5, 32)
            storeUInt(BigInt(queryId.toString()), 64)
            Coins.storeTlb(this, amount)
            MsgAddressInt.storeTlb(this, destination)
            MsgAddressInt.storeTlb(this, own)
            storeOptionalRef(custom)
            Coins.storeTlb(this, forwardAmount)
            storeOptionalRef(forward)
        }

    private fun CellBuilder.storeOptionalRef(ref: Cell?) {
        storeBit(ref != null)
        ref?.let(::storeRef)
    }

    private fun cell(build: CellBuilder.() -> Unit): Cell = CellBuilder.beginCell().apply(build).endCell()

    private companion object {
        const val SIGN_TX = 0x06
        const val APDU_HEADER = 5
        const val P2 = 3
        const val TIMEOUT = 1_893_456_000

        // The device signature is opaque to the signer, so every case answers with one of them.
        const val SIGNATURE =
            "e7dc285a2ea99219dc03d987bd19dc829dc6974ccb1ba4238fb7529b8fead98931bd9f7d28adc962210a11d91d8b9cc9eaa492d8d" +
                "197d65abfa24ee8b1294703"

        const val COMMENT_HASH = "e8bf086eb8c8c828a91d74212f557d9382ea856a8f1185c7f2ff2046c76139d1"
        const val COMMENT_REQUEST =
            "000000000770dbd8800459682f00002f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f0003000100" +
                "00f190dc3807908d8304a67bff2e908e44cb2a5afab6aa48088aecd5e86d552e560100000000000f6c65646765722d6b6974" +
                "2074657374"

        const val JETTON_HASH = "3e464d63823def109a8ee42259394be0193f50330b3377411412dee821ef632d"
        const val JETTON_REQUEST =
            "000000000870dbd8800402faf080003c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c0103000100" +
                "01380bafceb4dc42696cc663703c136bdd73f87363062b4c97e812bc556d637c8601000000010075010000000000003039030f" +
                "4240002f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f00d67b2fc518f85dde98667ab7555e2f" +
                "b3508ba37f23b8fb6f72fd54f5c83d26790001010100002c67ed9a6d2f1bb0901dd9d0c8a0412b4012d1992ae58a89d27eb180" +
                "0bfdc680"

        const val UNSAFE_HASH = "69aa5034d09bf146faea169d2e937e90e1e3714db04bcb8dd894f96d4afba5ad"
        const val UNSAFE_REQUEST =
            "000000000970dbd88003989680002f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f010100010000" +
                "563c2bfcc0ba5873c290cc2296ba7e355d8e687a6480ce792e1fc7cb1fd6984f00"

        const val JETTON_BIG_HASH = "3190fae27e2d2fabf21e10a8f3380ee6975146cb83262bbac289c01d83a70ee2"
        const val JETTON_BIG_REQUEST =
            "000000000b70dbd8800fffffffffffffffffffffffffffffff003c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c" +
                "3c3c3c3c3c3c0101010001bb3e870e015a0c658752e1e241534e8d5a04f5f041db490509fb78a99f01c63c01000178b72289" +
                "352ce32fcb511d2522bac2b75c5cc7c4323faa703d3246718b1fe6e9010000000100b101ffffffffffffffff0fffffffffffff" +
                "ffffffffffffffffff002f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f00d67b2fc518f85dde" +
                "98667ab7555e2fb3508ba37f23b8fb6f72fd54f5c83d26790100002bed51e772ea246231b1d42fc48add2d69ee9f3cb1b69de9" +
                "e2dcaec347f2a5940fffffffffffffffffffffffffffffff0100002c67ed9a6d2f1bb0901dd9d0c8a0412b4012d1992ae58a89" +
                "d27eb1800bfdc680"
    }
}
