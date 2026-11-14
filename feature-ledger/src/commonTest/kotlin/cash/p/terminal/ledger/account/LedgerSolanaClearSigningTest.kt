package cash.p.terminal.ledger.account

import cash.p.terminal.ledger.account.AccountFixtures.SOLANA_PUBLIC_KEY
import cash.p.terminal.ledger.account.AccountFixtures.SPL_MINT
import cash.p.terminal.ledger.account.AccountFixtures.signingKey
import cash.p.terminal.ledger.account.AccountFixtures.splTransferMessage
import cash.p.terminal.ledger.account.AccountFixtures.storedKey
import cash.p.terminal.ledger.ethereum.InMemoryPreferences
import cash.p.terminal.ledger.ethereum.LedgerCalClient
import cash.p.terminal.ledger.ethereum.LedgerTokenDescriptorStore
import cash.p.terminal.ledger.ethereum.SignedDescriptor
import cash.p.terminal.ledgerkit.device.LedgerModel
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

class LedgerSolanaClearSigningTest {

    private val store = LedgerTokenDescriptorStore(InMemoryPreferences())
    private val calClient = mockk<LedgerCalClient> {
        coEvery { certificate("nanosp", any(), any()) } returns CERTIFICATE
        coEvery { solanaTokenDescriptor(SPL_MINT) } returns TOKEN
        coEvery { solanaTrustedName(any(), CHALLENGE) } returns TRUSTED_NAME
    }
    private val device = FakeAccountApp(
        mapOf(GET_CHALLENGE to CHALLENGE, SIGN to SIGNATURE, LOAD_CERTIFICATE to ByteArray(0)),
    )

    @Test
    fun sign_splTransferOnline_loadsTokenAndOwnerBeforeSigning() {
        val signature = signer(LedgerSolanaClearSigning(calClient, store)).sign(splTransferMessage())

        assertEquals(
            listOf(COIN_META_CERT, TOKEN_INFO, CHALLENGE_REQUEST, TRUSTED_NAME_CERT, OWNER_INFO, SIGNING),
            sent(),
        )
        assertContentEquals(TRUSTED_NAME, device.apdus[4].data())
        assertContentEquals(SIGNATURE, signature)
    }

    @Test
    fun sign_trustedNameRequestFails_signsBlind() {
        coEvery { calClient.solanaTrustedName(any(), any()) } throws IOException("offline")

        val signature = signer(LedgerSolanaClearSigning(calClient, store)).sign(splTransferMessage())

        assertEquals(listOf(COIN_META_CERT, TOKEN_INFO, CHALLENGE_REQUEST, SIGNING), sent())
        assertContentEquals(SIGNATURE, signature)
    }

    @Test
    fun prepare_trustedNameTimesOut_leavesTransferToBlindSigning() = runTest {
        coEvery { calClient.solanaTrustedName(any(), any()) } coAnswers {
            delay(1.minutes)
            TRUSTED_NAME
        }

        LedgerSolanaClearSigning(calClient, store).prepare(device, NANO_S_PLUS, splTransferMessage())

        assertEquals(listOf(COIN_META_CERT, TOKEN_INFO, CHALLENGE_REQUEST), sent())
    }

    @Test
    fun prepare_malformedTrustedNameResponse_leavesTransferToBlindSigning() = runTest {
        val engine = MockEngine { request ->
            val body = if ("nft.api" in request.url.host) """{"descriptorType":"TrustedName"}""" else "[]"
            respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        store.save("pki:nanosp:trusted_name", CERTIFICATE)
        val client = LedgerCalClient(
            HttpClient(engine) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } },
        )

        LedgerSolanaClearSigning(client, store).prepare(device, NANO_S_PLUS, splTransferMessage())

        assertEquals(listOf(CHALLENGE_REQUEST), sent())
    }

    @Test
    fun prepare_nanoS_sendsNothingAndAsksNothing() = runTest {
        val unused = mockk<LedgerCalClient>()

        LedgerSolanaClearSigning(unused, store).prepare(device, "NanoS", splTransferMessage())

        assertEquals(emptyList(), sent())
    }

    @Test
    fun calDevice_everyModel_namesItsCalTargetOrNoneWithoutPki() {
        val expected = mapOf(
            LedgerModel.NanoS to null,
            LedgerModel.NanoX to "nanox",
            LedgerModel.NanoSPlus to "nanosp",
            LedgerModel.Stax to "stax",
            LedgerModel.Flex to "flex",
            LedgerModel.NanoGen5 to "apexp",
            LedgerModel.Unknown to null,
        )

        assertEquals(expected, LedgerModel.entries.associateWith { LedgerSolanaClearSigning.calDevice(it.name) })
    }

    @Test
    fun sign_withoutClearSigning_sendsOnlyTheSignApdu() {
        signer(clearSigning = null).sign(splTransferMessage())

        assertEquals(listOf(SIGNING), sent())
    }

    @Test
    fun prefetch_twice_fetchesCertificatesAndTokenOnce() = runTest {
        val clearSigning = LedgerSolanaClearSigning(calClient, store)

        clearSigning.prefetch(NANO_S_PLUS, SPL_MINT)
        clearSigning.prefetch(NANO_S_PLUS, SPL_MINT)

        coVerify(exactly = 1) { calClient.certificate("nanosp", "trusted_name", "domain_metadata_key") }
        coVerify(exactly = 1) { calClient.certificate("nanosp", "coin_meta", "token_metadata_key") }
        coVerify(exactly = 1) { calClient.solanaTokenDescriptor(SPL_MINT) }
    }

    private fun signer(clearSigning: LedgerSolanaClearSigning?) =
        LedgerSolanaSigner(signingKey(device, storedKey(spl, SOLANA_PUBLIC_KEY)), clearSigning)

    // CLA, INS and P1 of every APDU sent, in order.
    private fun sent() = device.apdus.map { it.copyOf(3).toHexString() }

    private fun ByteArray.data() = copyOfRange(APDU_HEADER, size)

    private companion object {
        const val NANO_S_PLUS = "NanoSPlus"
        const val APDU_HEADER = 5
        const val GET_CHALLENGE = 0x20
        const val SIGN = 0x06
        const val LOAD_CERTIFICATE = 0xB006
        const val COIN_META_CERT = "b00608"
        const val TRUSTED_NAME_CERT = "b00604"
        const val TOKEN_INFO = "e02200"
        const val CHALLENGE_REQUEST = "e02000"
        const val OWNER_INFO = "e02100"
        const val SIGNING = "e00601"
        val spl = TokenQuery(BlockchainType.Solana, TokenType.Spl(SPL_MINT))
        val CHALLENGE = byteArrayOf(0x0a, 0x1b, 0x2c, 0x3d)
        val SIGNATURE = ByteArray(64) { it.toByte() }
        val TRUSTED_NAME = byteArrayOf(1, 1, 3)
        val CERTIFICATE = SignedDescriptor(byteArrayOf(1, 2), byteArrayOf(0x30, 1))
        val TOKEN = SignedDescriptor(byteArrayOf(3, 4), byteArrayOf(0x30, 2))
    }
}
