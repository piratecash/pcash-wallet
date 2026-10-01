package cash.p.terminal.modules.multiswap.providers.backendswap

import cash.p.terminal.core.installEthereumCryptoProviderForTest
import cash.p.terminal.core.managers.EvmMessageSigning
import cash.p.terminal.core.to0xHexString
import cash.p.terminal.modules.multiswap.providers.backendswap.BackendSwapGoldenVectors.assertSameSignature
import cash.p.terminal.modules.multiswap.providers.backendswap.BackendSwapGoldenVectors.softwareAddress
import cash.p.terminal.modules.multiswap.providers.backendswap.BackendSwapGoldenVectors.softwareSigner
import cash.p.terminal.network.backendswap.domain.entity.BackendSwapCreateRequest
import io.horizontalsystems.ethereumkit.crypto.EIP712Encoder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.web3j.crypto.StructuredDataEncoder

class BackendSwapTypedDataTest {

    @Before
    fun setUp() {
        installEthereumCryptoProviderForTest()
    }

    @Test
    fun toTypedDataJson_messageWithoutOptionals_digestMatchesEthers() {
        val json = BackendSwapGoldenVectors.m1.toTypedDataJson()

        assertEquals(BackendSwapGoldenVectors.M1_DIGEST, EIP712Encoder().encodeTypedDataHash(json).to0xHexString())
    }

    @Test
    fun toTypedDataJson_messageWithAllOptionals_digestMatchesEthers() {
        val json = BackendSwapGoldenVectors.m2.toTypedDataJson()

        assertEquals(BackendSwapGoldenVectors.M2_DIGEST, EIP712Encoder().encodeTypedDataHash(json).to0xHexString())
    }

    @Test
    fun toTypedDataJson_domainWithoutChainId_domainAndStructHashesMatchEthers() {
        val encoder = StructuredDataEncoder(BackendSwapGoldenVectors.m1.toTypedDataJson())
        @Suppress("UNCHECKED_CAST")
        val message = encoder.jsonMessageObject.message as HashMap<String, Any>

        assertEquals(BackendSwapGoldenVectors.DOMAIN_SEPARATOR, encoder.hashDomain().to0xHexString())
        assertEquals(
            BackendSwapGoldenVectors.M1_STRUCT_HASH,
            encoder.hashMessage(encoder.jsonMessageObject.primaryType, message).to0xHexString()
        )
    }

    @Test
    fun signTypedData_softwareSigner_reproducesEthersSignatures() = runTest {
        val signer = softwareSigner()

        assertEquals(BackendSwapGoldenVectors.ADDRESS, softwareAddress().eip55)
        listOf(
            BackendSwapGoldenVectors.m1 to BackendSwapGoldenVectors.M1_SIGNATURE,
            BackendSwapGoldenVectors.m2 to BackendSwapGoldenVectors.M2_SIGNATURE,
        ).forEach { (message, expected) ->
            val signature = EvmMessageSigning.signTypedData(signer, message.toTypedDataJson()).to0xHexString()
            assertSameSignature(expected, signature)
        }
    }

    @Test
    fun toCreateRequest_absentOptionals_areOmittedWhileSignedAsEmpty() {
        val message = BackendSwapGoldenVectors.m1

        val request = message.toCreateRequest("0xsig")

        assertEquals(
            BackendSwapCreateRequest(
                provider = "changelly",
                type = "float",
                from = message.from,
                to = message.to,
                amount = message.amount,
                recipient = message.recipient,
                clientRequestId = message.clientRequestId,
                signature = "0xsig",
            ),
            request
        )
        val encoder = StructuredDataEncoder(message.toTypedDataJson())
        @Suppress("UNCHECKED_CAST")
        val signed = encoder.jsonMessageObject.message as Map<String, Any>
        OPTIONAL_FIELDS.forEach { field -> assertEquals(field, "", signed[field]) }
    }

    private companion object {
        val OPTIONAL_FIELDS = listOf("recipientExtraId", "refundAddress", "refundExtraId", "fromAddress", "fromExtraId")
    }
}
