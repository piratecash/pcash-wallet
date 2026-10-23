package cash.p.terminal.ledger.account

import cash.p.terminal.ledger.account.AccountFixtures.THORCHAIN_ADDRESS
import cash.p.terminal.ledger.account.AccountFixtures.THORCHAIN_PUBLIC_KEY
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
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Sign docs and signatures: the speculos seed's key, signed independently with RFC 6979 (ledger-kit vectors). */
class LedgerThorchainSignerTest {

    private val rune = TokenQuery(BlockchainType.Thorchain, TokenType.Native)
    private val stored = storedKey(rune, THORCHAIN_ADDRESS, THORCHAIN_PUBLIC_KEY.hexToByteArray())

    @Test
    fun signAmino_msgSend_sendsDocAtLedgerPathAndReturnsCompactSignature() = runTest {
        val device = signingApp(SEND_SIGNATURE)
        val signer = LedgerThorchainSigner(signingKey(device, stored))

        val signature = signer.signAmino(SEND_DOC.encodeToByteArray())

        assertEquals(SEND_SIGNATURE, signature.toHexString())
        assertContentEquals(THORCHAIN_PUBLIC_KEY.hexToByteArray(), signer.publicKey)
        assertEquals(PATH + SEND_DOC.encodeToByteArray().toHexString(), device.request())
    }

    @Test
    fun signAmino_msgDeposit_returnsCompactSignature() = runTest {
        val device = signingApp(DEPOSIT_SIGNATURE)

        val signature = LedgerThorchainSigner(signingKey(device, stored)).signAmino(DEPOSIT_DOC.encodeToByteArray())

        assertEquals(DEPOSIT_SIGNATURE, signature.toHexString())
    }

    @Test
    fun signAmino_otherDeviceConnected_neverSendsDoc() = runTest {
        val device = FakeAccountApp()
        val access = mockk<LedgerAppAccess> {
            coEvery { signWithLedgerApp<Any>(any(), any(), any()) } throws LedgerIdentityMismatchException()
        }
        val signer = LedgerThorchainSigner(LedgerSigningKey(access, account, stored))

        assertFailsWith<LedgerIdentityMismatchException> { signer.signAmino(SEND_DOC.encodeToByteArray()) }
        assertTrue(device.apdus.isEmpty())
    }

    @Test
    fun signAmino_deviceAnswers_mapsRejectionAndSize() = runTest {
        assertFailsWith<LedgerException.UserRejected> { signer(failingDevice(0x6986)).signAmino(DOC) }
        assertFailsWith<LedgerException.DataTooLarge> { signer(failingDevice(0x6983)).signAmino(DOC) }
        // An index above 100 without expert mode.
        assertFailsWith<LedgerException.InvalidRequest> { signer(failingDevice(0x6985)).signAmino(DOC) }
    }

    private fun signer(device: LedgerTransport) = LedgerThorchainSigner(signingKey(device, stored))

    private fun signingApp(signature: String) =
        FakeAccountApp(mapOf(SIGN to der(signature)), isLastChunk = { it[P1].toInt() == P1_LAST })

    private fun failingDevice(statusWord: Int) = object : LedgerTransport {
        override suspend fun exchange(apdu: ByteArray) =
            byteArrayOf((statusWord shr 8).toByte(), statusWord.toByte())

        override fun close() = Unit
    }

    // All data of the chunked request, path chunk first.
    private fun FakeAccountApp.request(): String =
        apdus.joinToString("") { it.copyOfRange(APDU_HEADER, it.size).toHexString() }

    /** The app answers with a DER signature; r and s of the vectors need no sign byte. */
    private fun der(compact: String): ByteArray {
        val bytes = compact.hexToByteArray()
        val r = byteArrayOf(0x02, 32) + bytes.copyOfRange(0, 32)
        val s = byteArrayOf(0x02, 32) + bytes.copyOfRange(32, 64)
        return byteArrayOf(0x30, (r.size + s.size).toByte()) + r + s
    }

    private companion object {
        const val SIGN = 0x5502
        const val APDU_HEADER = 5
        const val P1 = 2
        const val P1_LAST = 2
        val DOC = "{}".encodeToByteArray()

        // 44'/931'/0'/0/0 as little-endian uint32s.
        const val PATH = "2c000080a3030080000000800000000000000000"

        const val SEND_DOC =
            """{"account_number":"123","chain_id":"thorchain-1","fee":{"amount":[],"gas":"50000000"},""" +
                """"memo":"ledger-kit test","msgs":[{"type":"thorchain/MsgSend","value":{"amount":[{"amount":""" +
                """"150000000","denom":"rune"}],"from_address":"thor19hzdfmktztey2vuqpa3tv7dqe0kw3u24km6yca",""" +
                """"to_address":"thor1mwyrp6lj85swy5e5g4hjlaacm33g6rw3p4qmq4"}}],"sequence":"456"}"""
        const val SEND_SIGNATURE =
            "2f25b7c73bcdf0aa48a064352b7b4b19452a99d1cf508acdfca52e58c4548f2c64831512675ac3cf3e9bf0c724dfdc0ec924c1" +
                "1da493b02fc20857f0a46a1d01"

        const val DEPOSIT_DOC =
            """{"account_number":"123","chain_id":"thorchain-1","fee":{"amount":[],"gas":"50000000"},"memo":"",""" +
                """"msgs":[{"type":"thorchain/MsgDeposit","value":{"coins":[{"amount":"100000000","asset":""" +
                """"THOR.RUNE"}],"memo":"=:BTC.BTC:bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq:0/1/0",""" +
                """"signer":"thor19hzdfmktztey2vuqpa3tv7dqe0kw3u24km6yca"}}],"sequence":"456"}"""
        const val DEPOSIT_SIGNATURE =
            "3f8124310136b2a775fcb4aac8113700f87b81f83c62b4cd09cd30e51c1cad3a5a256f14226d158d1055a4b42fb5c9852f995d" +
                "7ef078c2b7a04b01b92bb4cec0"
    }
}
