package cash.p.terminal.trezor.signer

import cash.p.terminal.trezor.client.TrezorDerivationPath
import cash.p.terminal.trezor.domain.TrezorSigningException
import cash.p.terminal.trezor.domain.model.TrezorModel
import cash.p.terminal.trezorkit.client.ITrezorClient
import cash.p.terminal.trezorkit.client.TrezorClientSession
import cash.p.terminal.trezorkit.client.TrezorEvmGasFee
import cash.p.terminal.trezorkit.client.TrezorEvmTx
import cash.p.terminal.trezorkit.client.TrezorMessageSignature
import cash.p.terminal.trezorkit.client.TrezorSignature
import cash.p.terminal.trezorkit.client.TrezorTypedData
import cash.p.terminal.trezorkit.client.TrezorTypedMember
import cash.p.terminal.trezorkit.client.TrezorTypedValue
import cash.p.terminal.wallet.crypto.EvmSignatureRecovery
import io.horizontalsystems.ethereumkit.crypto.CryptoUtils
import io.horizontalsystems.ethereumkit.crypto.InternalBouncyCastleProvider
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.Chain
import io.horizontalsystems.ethereumkit.models.GasPrice
import io.horizontalsystems.ethereumkit.models.RawTransaction
import io.horizontalsystems.ethereumkit.spv.core.toBytes
import io.horizontalsystems.hdwalletkit.ECKey
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.math.BigInteger
import java.security.Security

class TrezorEvmSignerTest {

    // Fake ITrezorClient whose connect(block) runs the block on a mocked TrezorClientSession, so the
    // signer exercises the real connect() path and we can stub/capture signEthereum.
    private val session: TrezorClientSession = mockk()
    private val trezorClient = object : ITrezorClient {
        override suspend fun <T> connect(block: suspend TrezorClientSession.() -> T): T = session.block()
    }
    private var connectCount = 0
    private val countingClient = object : ITrezorClient {
        override suspend fun <T> connect(block: suspend TrezorClientSession.() -> T): T {
            connectCount++
            return session.block()
        }
    }
    private val txSlot = slot<TrezorEvmTx>()

    @Before
    fun setUp() {
        Security.addProvider(InternalBouncyCastleProvider.getInstance())
    }

    // Real capture from a Trezor Safe 5 BSC token approve (legacy tx). The requested gasLimit 0xe8f0
    // (59632) differs from the signed gasLimit 0x065053 (413779) - exercises gas reconciliation.
    private val toAddress = "0xba2ae424d960c26247dd6c32edc70b295c744c43"
    private val data = "0x095ea7b300000000000000000000000013f4ea83d0bd40e75c8222255bc855a974568dd4" +
        "000000000000000000000000000000000000000000000000000000003b034361"
    private val requestedGasLimit = 59632L
    private val signedGasLimit = 413779L
    private val gasPrice = 50_000_000L
    private val nonce = 267L

    // The account that actually signed the captured transaction.
    private val legacySender = "0x24b1bf74dc962ff109048c2e540864324fe6622d"

    private val responseV = "0x94"
    private val responseR = "0xd752d2389177121be79640f794bcbe8036e14cdfc7a2f4a0a11bbfece058b722"
    private val responseS = "0x7a5d0c972c4e44f9d1586cbd629d5db87182554449de9414e4d9eb27b32c72fe"
    private val serializedTx = "0xf8ac82010b8402faf0808306505394ba2ae424d960c26247dd6c32edc70b295c744c43" +
        "80b844095ea7b300000000000000000000000013f4ea83d0bd40e75c8222255bc855a974568dd400000000" +
        "0000000000000000000000000000000000000000000000003b0343618194a0d752d2389177121be79640f7" +
        "94bcbe8036e14cdfc7a2f4a0a11bbfece058b722a07a5d0c972c4e44f9d1586cbd629d5db87182554449de9" +
        "414e4d9eb27b32c72fe"

    // Deterministic EIP-1559 BSC vector (private key 0x4646...46). Requested gasLimit differs from
    // the signed one to exercise gas-limit reconciliation on the typed-transaction path too.
    private val eip1559Sender = "0x9d8a62f656a8d1615c1294fd71e9cfb3e4855a4f"
    private val eip1559Nonce = 5L
    private val eip1559MaxFee = 3_000_000_000L
    private val eip1559MaxPriorityFee = 1_000_000_000L
    private val eip1559SignedGasLimit = 250_000L
    private val eip1559ResponseV = "0x0"
    private val eip1559ResponseR = "0x101fdd98aa2871e985499dedc4de19810db6cbcf6fcb376fedf4cb56cf0277c5"
    private val eip1559ResponseS = "0x711751862e80fcf8e1e2dd0d387e8d0248738e6a9a92357a8f5d035f0c820c60"
    private val eip1559SerializedTx = "0x02f8b03805843b9aca0084b2d05e008303d09094ba2ae424d960c26247dd6c32" +
        "edc70b295c744c4380b844095ea7b300000000000000000000000013f4ea83d0bd40e75c8222255bc855a974568dd4" +
        "000000000000000000000000000000000000000000000000000000003b034361c080a0101fdd98aa2871e985499dedc" +
        "4de19810db6cbcf6fcb376fedf4cb56cf0277c5a0711751862e80fcf8e1e2dd0d387e8d0248738e6a9a92357a8f5d03" +
        "5f0c820c60"

    // P.Cash CreateExchange m1 vector; signed by throwaway key 0x4c0883a6...3f362318 (ethers, v = 0x1b).
    private val typedDataSender = "0x2c7536E3605D9C16a7a3D7b1898e529396a65c23"
    private val m1DomainHash = "0x70e10b7ae1cd9e2a8a24d21c0f9c9e46340e5aeb20e6d0c337cd78a6fec9d068"
    private val m1MessageHash = "0x9eb9c202dc4ecb182acf1706506b614f23e86d72d84c68e4ffcb073ce1de3b17"
    private val m1Digest = "0x20a4727a0a4f04220791a7bce431a576fc7aa642513d8840be90ed6340b33c1a"
    private val m1Signature = "0x9a2c1d4822237a85fcdc69f8cd656353ce0bc5f6dd87f366513ff48a1f03b8f6" +
        "0675c8e34b8508de03f927aac931d8f386161d3489c5177b7fb85fb44f9fb6361b"
    private val m1Json = """
        {
          "types": {
            "EIP712Domain": [{"name": "name", "type": "string"}, {"name": "version", "type": "string"}],
            "Asset": [{"name": "coinId", "type": "string"}, {"name": "blockchain", "type": "string"}],
            "CreateExchange": [
              {"name": "from", "type": "Asset"}, {"name": "to", "type": "Asset"},
              {"name": "amount", "type": "string"}, {"name": "type", "type": "string"},
              {"name": "recipient", "type": "string"}, {"name": "recipientExtraId", "type": "string"},
              {"name": "refundAddress", "type": "string"}, {"name": "refundExtraId", "type": "string"},
              {"name": "fromAddress", "type": "string"}, {"name": "fromExtraId", "type": "string"},
              {"name": "provider", "type": "string"}, {"name": "clientRequestId", "type": "string"}
            ]
          },
          "primaryType": "CreateExchange",
          "domain": {"name": "P.Cash Exchange", "version": "1"},
          "message": {
            "from": {"coinId": "bitcoin", "blockchain": "bitcoin"},
            "to": {"coinId": "ethereum", "blockchain": "ethereum"},
            "amount": "0.01",
            "type": "float",
            "recipient": "0x1234567890abcdef1234567890abcdef12345678",
            "recipientExtraId": "",
            "refundAddress": "",
            "refundExtraId": "",
            "fromAddress": "",
            "fromExtraId": "",
            "provider": "changelly",
            "clientRequestId": "8d3d9db0-7d1b-43ce-9431-fb530ce2c6ef"
          }
        }
    """.trimIndent()

    @Test
    fun signTransaction_signedGasLimitDiffers_reconcilesToSignedGasLimit() = runBlocking {
        stubSignature(legacySignature())
        val rawTransaction = requestedRawTransaction()

        val signed = createSigner().signTransaction(rawTransaction)

        assertEquals(signedGasLimit, signed.rawTransaction.gasLimit)
        assertEquals(gasPrice, (signed.rawTransaction.gasPrice as GasPrice.Legacy).legacyGasPrice)
        assertEquals(rawTransaction.nonce, signed.rawTransaction.nonce)
        assertEquals(rawTransaction.to, signed.rawTransaction.to)
        assertEquals(rawTransaction.value, signed.rawTransaction.value)
        assertArrayEquals(rawTransaction.data, signed.rawTransaction.data)
    }

    @Test
    fun signTransaction_validResponse_parsesSignature() = runBlocking {
        stubSignature(legacySignature())

        val signed = createSigner().signTransaction(requestedRawTransaction())

        assertEquals(148, signed.signature.v)
        assertArrayEquals(responseR.hexToBytes(), signed.signature.r)
        assertArrayEquals(responseS.hexToBytes(), signed.signature.s)
    }

    @Test
    fun signTransaction_mapsRawTransactionToTrezorEvmTx() = runBlocking {
        stubSignature(legacySignature())
        val raw = requestedRawTransaction()

        createSigner().signTransaction(raw)

        val tx = txSlot.captured
        assertEquals(TrezorDerivationPath.parse("m/44'/60'/0'/0/0"), tx.addressN)
        assertEquals(nonce, BigInteger(1, tx.nonce).toLong())
        assertEquals(requestedGasLimit, BigInteger(1, tx.gasLimit).toLong())
        assertEquals(raw.to.hex, tx.to)
        assertArrayEquals(raw.data, tx.data)
        assertEquals(Chain.BinanceSmartChain.id.toLong(), tx.chainId)
        val gasFee = tx.gasFee as TrezorEvmGasFee.Legacy
        assertEquals(gasPrice, BigInteger(1, gasFee.gasPrice).toLong())
    }

    @Test
    fun signTransaction_highBitValue_trimmedToMinimalBigEndian() = runBlocking {
        stubSignature(legacySignature())
        // 0x80: BigInteger.toByteArray() prepends a 0x00 sign byte for positive high-bit values;
        // the mapper must trim it to the minimal unsigned big-endian form Trezor expects.
        val raw = RawTransaction(
            gasPrice = GasPrice.Legacy(gasPrice),
            gasLimit = requestedGasLimit,
            to = Address(toAddress),
            value = BigInteger.valueOf(0x80),
            nonce = nonce,
            data = data.hexToBytes()
        )

        runCatchingSign { createSigner().signTransaction(raw) }

        assertArrayEquals(byteArrayOf(0x80.toByte()), txSlot.captured.value)
    }

    @Test
    fun signTransaction_zeroValue_encodesEmptyByteArray() = runBlocking {
        stubSignature(legacySignature())

        createSigner().signTransaction(requestedRawTransaction())

        assertArrayEquals(ByteArray(0), txSlot.captured.value)
    }

    @Test
    fun signTransaction_eip1559_mapsFeeFieldsToTrezorEvmTx() = runBlocking {
        stubSignature(eip1559Signature())

        createSigner(eip1559Sender).signTransaction(eip1559RequestedRawTransaction())

        val gasFee = txSlot.captured.gasFee as TrezorEvmGasFee.Eip1559
        assertEquals(eip1559MaxFee, BigInteger(1, gasFee.maxFeePerGas).toLong())
        assertEquals(eip1559MaxPriorityFee, BigInteger(1, gasFee.maxPriorityFeePerGas).toLong())
    }

    @Test
    fun signTransaction_eip1559SignedGasLimitDiffers_reconcilesGasFields() = runBlocking {
        stubSignature(eip1559Signature())
        val requested = eip1559RequestedRawTransaction()

        val signed = createSigner(eip1559Sender).signTransaction(requested)

        val signedGasPrice = signed.rawTransaction.gasPrice as GasPrice.Eip1559
        assertEquals(eip1559SignedGasLimit, signed.rawTransaction.gasLimit)
        assertEquals(eip1559MaxFee, signedGasPrice.maxFeePerGas)
        assertEquals(eip1559MaxPriorityFee, signedGasPrice.maxPriorityFeePerGas)
        assertEquals(requested.nonce, signed.rawTransaction.nonce)
        assertEquals(requested.to, signed.rawTransaction.to)
        assertEquals(requested.value, signed.rawTransaction.value)
        assertArrayEquals(requested.data, signed.rawTransaction.data)
    }

    @Test
    fun signTransaction_signedGasPriceDiffers_reconcilesToSignedGasPrice() = runBlocking {
        stubSignature(legacySignature())
        // Request a gasPrice that differs from the one the device signed. Reconciliation must adopt
        // the device's value; keeping the requested one would break the byte-equality check.
        val requested = RawTransaction(
            gasPrice = GasPrice.Legacy(gasPrice * 10),
            gasLimit = requestedGasLimit,
            to = Address(toAddress),
            value = BigInteger.ZERO,
            nonce = nonce,
            data = data.hexToBytes()
        )

        val signed = createSigner().signTransaction(requested)

        assertEquals(gasPrice, (signed.rawTransaction.gasPrice as GasPrice.Legacy).legacyGasPrice)
    }

    @Test
    fun signTransaction_eip1559SignedFeesDiffer_reconcilesToSignedFees() = runBlocking {
        stubSignature(eip1559Signature())
        // Request fee fields that differ from those the device signed; the signed values must win.
        val requested = RawTransaction(
            gasPrice = GasPrice.Eip1559(
                maxFeePerGas = eip1559MaxFee * 2,
                maxPriorityFeePerGas = eip1559MaxPriorityFee * 2
            ),
            gasLimit = requestedGasLimit,
            to = Address(toAddress),
            value = BigInteger.ZERO,
            nonce = eip1559Nonce,
            data = data.hexToBytes()
        )

        val signed = createSigner(eip1559Sender).signTransaction(requested)

        val signedGasPrice = signed.rawTransaction.gasPrice as GasPrice.Eip1559
        assertEquals(eip1559MaxFee, signedGasPrice.maxFeePerGas)
        assertEquals(eip1559MaxPriorityFee, signedGasPrice.maxPriorityFeePerGas)
    }

    @Test
    fun signTransaction_missingSerializedTx_throws() {
        stubSignature(legacySignature().copy(serializedTx = null))

        assertThrows(TrezorSigningException::class.java) {
            runBlocking { createSigner().signTransaction(requestedRawTransaction()) }
        }
    }

    @Test
    fun signTransaction_rebuiltTransactionDiffersFromSignature_throws() {
        stubSignature(legacySignature())
        // The device signed value 0, but here we request a non-zero value. The rebuilt transaction
        // no longer re-encodes to the device's serializedTx, so reconciliation must fail loudly.
        val tampered = RawTransaction(
            gasPrice = GasPrice.Legacy(gasPrice),
            gasLimit = requestedGasLimit,
            to = Address(toAddress),
            value = BigInteger.ONE,
            nonce = nonce,
            data = data.hexToBytes()
        )

        assertThrows(TrezorSigningException::class.java) {
            runBlocking { createSigner().signTransaction(tampered) }
        }
    }

    @Test
    fun signTransaction_deviceSignedWithUnexpectedAccount_throws() {
        stubSignature(legacySignature())
        // The signer is configured with a different wallet address than the one that produced the
        // captured signature, so the sender guard must reject it.
        val wrongAddress = "0x000000000000000000000000000000000000dead"

        assertThrows(TrezorSigningException::class.java) {
            runBlocking { createSigner(wrongAddress).signTransaction(requestedRawTransaction()) }
        }
    }

    @Test
    fun signature_called_throws() {
        assertThrows(TrezorSigningException::class.java) {
            runBlocking { createSigner().signature(requestedRawTransaction()) }
        }
    }

    private fun legacySignature() = TrezorSignature(
        v = responseV.hexToInt(),
        r = responseR.hexToBytes(),
        s = responseS.hexToBytes(),
        serializedTx = serializedTx.hexToBytes()
    )

    @Test
    fun signPersonalMessage_validDeviceSignature_returnsRsRecId() = runBlocking {
        val privateKey = BigInteger("46".repeat(32), 16)
        val publicKeyBytes = ECKey.fromPrivate(privateKey.toBytes(32), compressed = false).pubKey
        val address = addressOf(publicKeyBytes)
        val message = "Sign in to near.com".toByteArray()
        val hash = EvmSignatureRecovery.personalSignHash(message)
        val ellipticSignature = CryptoUtils.ellipticSign(hash, privateKey)
        val recId = ellipticSignature[64].toInt()
        stubMessageSignature(deviceSignature(ellipticSignature, recId))

        val result = createSigner(address.hex).signPersonalMessage(message)

        assertEquals(65, result.size)
        assertArrayEquals(ellipticSignature.sliceArray(0..63), result.sliceArray(0..63))
        assertEquals(recId, result[64].toInt())

        val r = BigInteger(1, result.sliceArray(0..31))
        val s = BigInteger(1, result.sliceArray(32..63))
        assertEquals(address, EvmSignatureRecovery.recoverMessageAddress(hash, r, s, result[64].toInt()))
    }

    @Test
    fun signPersonalMessage_wrongAccountSignature_throws() {
        // The device signature is produced by a different private key than the account this
        // signer was constructed for, so the recovery-id resolution must find no match.
        val privateKey = BigInteger("64".repeat(32), 16)
        val message = "Sign in to near.com".toByteArray()
        val hash = EvmSignatureRecovery.personalSignHash(message)
        val ellipticSignature = CryptoUtils.ellipticSign(hash, privateKey)
        stubMessageSignature(deviceSignature(ellipticSignature, ellipticSignature[64].toInt()))

        val wrongAddress = Address("0x000000000000000000000000000000000000dead")

        assertThrows(TrezorSigningException::class.java) {
            runBlocking { createSigner(wrongAddress.hex).signPersonalMessage(message) }
        }
    }

    @Test
    fun signLegacyHash_always_throwsNotSupported() {
        assertThrows(TrezorSigningException::class.java) {
            runBlocking { createSigner().signLegacyHash(ByteArray(32)) }
        }
    }

    @Test
    fun signTypedDataMessage_safeModel_sendsConvertedTreeAndReturnsRsRecId() = runBlocking {
        val dataSlot = slot<TrezorTypedData>()
        coEvery { session.signEthereumTypedData(any(), capture(dataSlot)) } returns typedDataSignature(m1Signature)

        val result = createSigner(typedDataSender).signTypedDataMessage(m1Json)

        val data = dataSlot.captured
        assertEquals("CreateExchange", data.primaryType)
        assertEquals(
            listOf(TrezorTypedMember("name", "string"), TrezorTypedMember("version", "string")),
            data.types["EIP712Domain"]
        )
        assertEquals(
            TrezorTypedValue.Struct(mapOf("name" to primitive("P.Cash Exchange"), "version" to primitive("1"))),
            data.domain
        )
        assertEquals(
            TrezorTypedValue.Struct(mapOf("coinId" to primitive("bitcoin"), "blockchain" to primitive("bitcoin"))),
            data.message.fields["from"]
        )
        assertEquals(primitive(""), data.message.fields["recipientExtraId"])
        assertArrayEquals(m1Signature.hexToBytes().copyOf(64) + byteArrayOf(0), result)
    }

    @Test
    fun signTypedDataMessage_modelOne_sendsWeb3jHashes() = runBlocking {
        val domainSlot = slot<ByteArray>()
        val messageSlot = slot<ByteArray>()
        coEvery {
            session.signEthereumTypedHash(any(), capture(domainSlot), capture(messageSlot))
        } returns typedDataSignature(m1Signature)

        val result = createSigner(typedDataSender, TrezorModel.One).signTypedDataMessage(m1Json)

        assertArrayEquals(m1DomainHash.hexToBytes(), domainSlot.captured)
        assertArrayEquals(m1MessageHash.hexToBytes(), messageSlot.captured)
        assertArrayEquals(m1Signature.hexToBytes().copyOf(64) + byteArrayOf(0), result)
    }

    @Test
    fun signTypedDataMessage_signatureFromAnotherKey_throws() {
        val ellipticSignature = CryptoUtils.ellipticSign(m1Digest.hexToBytes(), BigInteger("64".repeat(32), 16))
        val signature = deviceSignature(ellipticSignature, ellipticSignature[64].toInt())
        coEvery { session.signEthereumTypedData(any(), any()) } returns
            TrezorMessageSignature(signature = signature, address = typedDataSender)

        assertThrows(TrezorSigningException::class.java) {
            runBlocking { createSigner(typedDataSender).signTypedDataMessage(m1Json) }
        }
    }

    @Test
    fun signTypedDataMessage_firmwareBelowMinimum_throwsWithoutConnecting() {
        listOf(TrezorModel.One to "1.10.4", TrezorModel.Safe5 to "2.4.2").forEach { (model, firmware) ->
            val signer = createSigner(typedDataSender, model, firmware, countingClient)

            assertThrows(TrezorSigningException::class.java) {
                runBlocking { signer.signTypedDataMessage(m1Json) }
            }
        }
        assertEquals(0, connectCount)
    }

    @Test
    fun signTypedDataMessage_domainOnlyPayload_throwsWithoutConnecting() {
        val domainOnlyJson = m1Json.replace("\"primaryType\": \"CreateExchange\"", "\"primaryType\": \"EIP712Domain\"")
        listOf(TrezorModel.One, TrezorModel.Safe5).forEach { model ->
            val signer = createSigner(typedDataSender, model, trezorClient = countingClient)

            assertThrows(TrezorSigningException::class.java) {
                runBlocking { signer.signTypedDataMessage(domainOnlyJson) }
            }
        }
        assertEquals(0, connectCount)
    }

    private fun typedDataSignature(hex: String) =
        TrezorMessageSignature(signature = hex.hexToBytes(), address = typedDataSender)

    private fun primitive(value: String) = TrezorTypedValue.Primitive(value)

    /** Builds the 65-byte `r‖s‖v` payload Trezor returns for `EthereumSignMessage` (v = 27/28). */
    private fun deviceSignature(ellipticSignature: ByteArray, recId: Int): ByteArray =
        ellipticSignature.sliceArray(0..63) + byteArrayOf((27 + recId).toByte())

    private fun stubMessageSignature(signature: ByteArray) {
        coEvery { session.signEthereumMessage(any(), any()) } returns
            TrezorMessageSignature(signature = signature, address = legacySender)
    }

    /** Derives the Ethereum address (last 20 bytes of keccak256 of the uncompressed key, minus the 0x04 prefix). */
    private fun addressOf(uncompressedPublicKeyBytes: ByteArray): Address =
        Address(CryptoUtils.sha3(uncompressedPublicKeyBytes.copyOfRange(1, 65)).copyOfRange(12, 32))

    private fun eip1559Signature() = TrezorSignature(
        v = eip1559ResponseV.hexToInt(),
        r = eip1559ResponseR.hexToBytes(),
        s = eip1559ResponseS.hexToBytes(),
        serializedTx = eip1559SerializedTx.hexToBytes()
    )

    private fun stubSignature(signature: TrezorSignature) {
        coEvery { session.signEthereum(capture(txSlot)) } returns signature
    }

    private fun requestedRawTransaction() = RawTransaction(
        gasPrice = GasPrice.Legacy(gasPrice),
        gasLimit = requestedGasLimit,
        to = Address(toAddress),
        value = BigInteger.ZERO,
        nonce = nonce,
        data = data.hexToBytes()
    )

    private fun eip1559RequestedRawTransaction() = RawTransaction(
        gasPrice = GasPrice.Eip1559(maxFeePerGas = eip1559MaxFee, maxPriorityFeePerGas = eip1559MaxPriorityFee),
        gasLimit = requestedGasLimit,
        to = Address(toAddress),
        value = BigInteger.ZERO,
        nonce = eip1559Nonce,
        data = data.hexToBytes()
    )

    private fun createSigner(
        address: String = legacySender,
        model: TrezorModel? = TrezorModel.Safe5,
        firmwareVersion: String = "2.8.10",
        trezorClient: ITrezorClient = this.trezorClient
    ) = TrezorEvmSigner(
        address = Address(address),
        chain = Chain.BinanceSmartChain,
        derivationPath = "m/44'/60'/0'/0/0",
        trezorClient = trezorClient,
        model = model,
        firmwareVersion = firmwareVersion
    )

    /** Runs a signing call for tests that only inspect the captured [TrezorEvmTx], ignoring later reconciliation. */
    private inline fun runCatchingSign(block: () -> Unit) {
        try {
            block()
        } catch (_: TrezorSigningException) {
            // Reconciliation may reject the mismatched fixture; the captured request is what matters here.
        }
    }

    private fun String.hexToBytes(): ByteArray =
        removePrefix("0x").chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun String.hexToInt(): Int = removePrefix("0x").toInt(16)
}
