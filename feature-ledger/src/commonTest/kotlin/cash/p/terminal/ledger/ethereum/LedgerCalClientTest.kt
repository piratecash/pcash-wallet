package cash.p.terminal.ledger.ethereum

import cash.p.terminal.ledger.account.AccountFixtures.SPL_DESTINATION
import cash.p.terminal.ledger.account.AccountFixtures.SPL_MINT
import cash.p.terminal.ledger.account.AccountFixtures.SPL_RECIPIENT
import cash.p.terminal.ledger.account.SolanaTokenTransfer
import cash.p.terminal.ledger.ethereum.EvmFixtures.USDT
import io.horizontalsystems.ethereumkit.core.toRawHexString
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LedgerCalClientTest {

    @Test
    fun erc20Descriptor_listedToken_prefixesTickerLengthAndAppendsProdSignature() = runTest {
        val engine = engine(USDT_RESPONSE)

        val descriptor = LedgerCalClient(client(engine)).erc20Descriptor(1, "0xdAC17F958D2ee523a2206206994597C13D831ec7")

        assertEquals("04$USDT_DATA$USDT_PROD_SIGNATURE", descriptor?.toRawHexString())
        val url = engine.requestHistory.single().url
        assertEquals("1", url.parameters["chain_id"])
        assertEquals(USDT, url.parameters["contract_address"])
    }

    @Test
    fun erc20Descriptor_unlistedToken_returnsNull() = runTest {
        assertNull(LedgerCalClient(client(engine("[]"))).erc20Descriptor(56, USDT))
    }

    @Test
    fun erc20Descriptor_dataNotStartingWithTicker_returnsNull() = runTest {
        val response = USDT_RESPONSE.replace("\"ticker\":\"USDT\"", "\"ticker\":\"USDC\"")

        assertNull(LedgerCalClient(client(engine(response))).erc20Descriptor(1, USDT))
    }

    @Test
    fun certificate_device_requestsLatestCertificateOfKeyAndReturnsProdSignature() = runTest {
        val engine = engine(CERTIFICATE_RESPONSE)

        val certificate = LedgerCalClient(client(engine)).certificate("nanosp", "trusted_name", "domain_metadata_key")

        assertEquals(CERTIFICATE_DATA, certificate?.data?.toRawHexString())
        assertEquals(CERTIFICATE_PROD_SIGNATURE, certificate?.signature?.toRawHexString())
        val parameters = engine.requestHistory.single().url.parameters
        assertEquals("nanosp", parameters["target_device"])
        assertEquals("trusted_name", parameters["public_key_usage"])
        assertEquals("domain_metadata_key", parameters["public_key_id"])
        assertEquals("true", parameters["latest"])
    }

    @Test
    fun certificate_deviceWithoutPki_returnsNull() = runTest {
        assertNull(LedgerCalClient(client(engine("[]"))).certificate("nanos", "trusted_name", "domain_metadata_key"))
    }

    @Test
    fun solanaTokenDescriptor_listedMint_returnsDataAndProdSignature() = runTest {
        val engine = engine(SOLANA_TOKEN_RESPONSE)

        val descriptor = LedgerCalClient(client(engine)).solanaTokenDescriptor(SPL_MINT)

        assertEquals(SOLANA_TOKEN_DATA, descriptor?.data?.toRawHexString())
        assertEquals("solana", engine.requestHistory.single().url.parameters["blockchain_name"])
    }

    @Test
    fun solanaTrustedName_newRecipient_asksForComputedAccountWithChallenge() = runTest {
        val engine = engine(TRUSTED_NAME_RESPONSE)
        val transfer = SolanaTokenTransfer(SPL_MINT, SPL_DESTINATION, newAccountOwner = SPL_RECIPIENT)

        val name = LedgerCalClient(client(engine)).solanaTrustedName(transfer, CHALLENGE)

        assertEquals(TRUSTED_NAME, name.toRawHexString())
        val url = engine.requestHistory.single().url
        assertEquals("/v2/solana/computed-token-account/$SPL_RECIPIENT/$SPL_MINT", url.encodedPath)
        assertEquals("0x0a1b2c3d", url.parameters["challenge"])
    }

    @Test
    fun solanaTrustedName_existingAccount_asksForItsOwner() = runTest {
        val engine = engine(TRUSTED_NAME_RESPONSE)

        val transfer = SolanaTokenTransfer(SPL_MINT, SPL_DESTINATION, newAccountOwner = null)

        LedgerCalClient(client(engine)).solanaTrustedName(transfer, CHALLENGE)

        assertEquals("/v2/solana/owner/$SPL_DESTINATION", engine.requestHistory.single().url.encodedPath)
    }

    private fun engine(body: String) = MockEngine {
        respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
    }

    private fun client(engine: MockEngine) = HttpClient(engine) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    internal companion object {
        val CHALLENGE = byteArrayOf(0x0a, 0x1b, 0x2c, 0x3d)

        // Recorded from crypto-assets-service.api.ledger.com and nft.api.live.ledger.com on 2026-10-16.
        const val CERTIFICATE_DATA = "01010102010235010336010110040200000013020002140101200c747275737465645f6e616d65" +
            "300200073101043201213401013321036a94e7a42cd0c33fdf440c8e2ab2542cefbe5db7aa0b93a9fc814b9acfa75eb4"
        const val CERTIFICATE_PROD_SIGNATURE = "3045022100e12b751e30d619570b177e98dd58f4a112cc0b0f7f6ac3db001b62ab" +
            "6438a49802206b0048a88d5483e082b9928412671e9863ea33506d4d6eb9579b4b66629b3f00"
        val CERTIFICATE_RESPONSE = """[{"id":"trusted_name/domain_metadata_key/nanosp/0.0.1","descriptor":""" +
            """{"data":"$CERTIFICATE_DATA","descriptorType":"certificate",""" +
            """"signatures":{"prod":"$CERTIFICATE_PROD_SIGNATURE","test":"3045"}}}]"""
        const val SOLANA_TOKEN_DATA = "0101900201010304800001f50406536f6c616e610504555344430601060733100100112c4550" +
            "6a465764643541756671535371654d32714e31787a7962617043384734774547476b5a777954447431761200"
        val SOLANA_TOKEN_RESPONSE = """[{"descriptor":{"data":"$SOLANA_TOKEN_DATA","descriptorType":"token",""" +
            """"signatures":{"prod":"3044022072409f98","test":"3045"}}}]"""
        const val TRUSTED_NAME = "010103020102700106710106202c464745546f385438774d634e3277436a617638564b36656833" +
            "644c6b363365764e4450787a4c534a7261384212040a1b2c3d13010714010115463044022000"
        val TRUSTED_NAME_RESPONSE = """{"descriptorType":"TrustedName","descriptorVersion":2,""" +
            """"tokenAccount":"$SPL_DESTINATION","owner":"$SPL_RECIPIENT","contract":"$SPL_MINT",""" +
            """"signedDescriptor":"$TRUSTED_NAME"}"""

        const val USDT_DATA = "55534454dac17f958d2ee523a2206206994597c13d831ec70000000600000001"
        const val USDT_PROD_SIGNATURE = "3044022078c66ccea3e4dedb15a24ec3c783d7b582cd260daf62fd36afe9a8212a344aed0220" +
            "160ba8c1c4b6a8aa6565bed20632a091aeeeb7bfdac67fc6589a6031acbf511c"

        // Recorded from crypto-assets-service.api.ledger.com on 2026-10-16.
        val USDT_RESPONSE = """[{"ticker":"USDT","descriptor":{"data":"$USDT_DATA","descriptorType":"token",""" +
            """"signatures":{"prod":"$USDT_PROD_SIGNATURE","test":"3045022100"}}}]"""
    }
}
