package cash.p.terminal.core.managers

import android.util.Base64
import cash.p.terminal.entities.OfflineBeamMetadata
import cash.p.terminal.core.managers.OfflineTransactionPayloadEncoder.DecodeResult
import cash.p.terminal.entities.OfflineSignedTransactionDraft
import cash.p.terminal.entities.OfflineStellarRetryMetadata
import cash.p.terminal.entities.OfflineSolanaRetryMetadata
import cash.p.terminal.entities.OfflineTonRetryMetadata
import cash.p.terminal.entities.OfflineTronRetryMetadata
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.hdwalletkit.Base58
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.security.MessageDigest
import java.util.zip.Deflater
import java.util.Base64 as JavaBase64

class OfflineTransactionPayloadEncoderTest {

    private lateinit var encoder: OfflineTransactionPayloadEncoder

    @Before
    fun setup() {
        // Route Android's Base64 through the JVM url-safe codec so encode/decode round-trip for real.
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } answers {
            JavaBase64.getUrlEncoder().withoutPadding().encodeToString(firstArg())
        }
        every { Base64.decode(any<String>(), any()) } answers {
            JavaBase64.getUrlDecoder().decode(firstArg<String>())
        }
        encoder = OfflineTransactionPayloadEncoder()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun decode_validRoundTrip_returnsEquivalentFields() {
        val payload = encoder.encode(draft())

        val decoded = encoder.decode(payload)

        assertNotNull(decoded)
        requireNotNull(decoded)
        assertEquals("bitcoin", decoded.blockchainUid)
        assertEquals("deadbeefdeadbeef", decoded.rawHex)
        assertEquals(TX_HASH, decoded.txHash)
        assertEquals("bitcoin|native", decoded.token.tokenQueryId)
        assertEquals("bitcoin", decoded.token.coinUid)
        assertEquals("BTC", decoded.token.coinCode)
        assertEquals("Bitcoin", decoded.token.coinName)
        assertEquals(8, decoded.token.decimals)
        assertEquals("100000", decoded.amountAtomic)
        assertEquals("bitcoin|native", decoded.fee?.tokenQueryId)
        assertEquals("1000", decoded.fee?.atomic)
        assertEquals(8, decoded.fee?.decimals)
        assertEquals("bc1qexampleaddrxyz", decoded.toAddress)
        assertEquals(CREATED_AT, decoded.createdAt)
    }

    @Test
    fun decode_eip20RoundTrip_usesTokenMetadataAndNativeFeeDecimals() {
        val payload = encoder.encode(
            draft(
                token = token(
                    blockchainType = BlockchainType.BinanceSmartChain,
                    blockchainName = "BNB Smart Chain",
                    coin = Coin(uid = "usd-coin", name = "USD Coin", code = "USDC"),
                    tokenType = TokenType.Eip20("0x8ac76a51cc950d9822d68b83fe1ad97b32cd580d"),
                    decimals = 6,
                ),
                feeToken = token(
                    blockchainType = BlockchainType.BinanceSmartChain,
                    blockchainName = "BNB Smart Chain",
                    coin = Coin(uid = "binance-coin", name = "BNB", code = "BNB"),
                    decimals = 18,
                ),
                amount = BigDecimal("12.345678"),
                fee = BigDecimal("0.000000000000123456"),
            )
        )

        val decoded = requireNotNull(encoder.decode(payload))

        assertEquals("binance-smart-chain", decoded.blockchainUid)
        assertEquals(
            "binance-smart-chain|eip20:0x8ac76a51cc950d9822d68b83fe1ad97b32cd580d",
            decoded.token.tokenQueryId,
        )
        assertEquals("USDC", decoded.token.coinCode)
        assertEquals(6, decoded.token.decimals)
        assertEquals("12345678", decoded.amountAtomic)
        assertEquals("binance-smart-chain|native", decoded.fee?.tokenQueryId)
        assertEquals(18, decoded.fee?.decimals)
        assertEquals("123456", decoded.fee?.atomic)
    }

    @Test
    fun decode_uppercaseRawHex_isNormalisedToLowercase() {
        val payload = encoder.encode(draft(rawHex = "DEADBEEF"))

        assertEquals("deadbeef", encoder.decode(payload)?.rawHex)
    }

    @Test
    fun decode_pathBlockchainUidContradictsBody_returnsNull() {
        val payload = encoder.encode(draft())
        // Swap only the authoritative path segment; the body still claims "bitcoin".
        val parts = payload.split(":", limit = 5)
        val spoofed = listOf(parts[0], parts[1], parts[2], "litecoin", parts[4]).joinToString(":")

        assertNull(encoder.decode(spoofed))
    }

    @Test
    fun decode_unknownPrefixOrVersion_returnsNull() {
        assertNull(encoder.decode("not-a-payload"))
        assertNull(encoder.decode("pcash:tx:v2:bitcoin:body"))
        assertNull(encoder.decode("pcash:other:v1:bitcoin:body"))
    }

    @Test
    fun decode_malformedBase64Body_returnsNull() {
        assertNull(encoder.decode("pcash:tx:v1:bitcoin:@@@not-base64@@@"))
    }

    @Test
    fun decode_corruptedBody_returnsNull() {
        val payload = encoder.encode(draft())
        val parts = payload.split(":", limit = 5)
        val body = parts[4]
        // Flip the first body byte: the deflate stream no longer inflates / no longer checksums.
        val corruptedFirst = if (body[0] == 'A') 'B' else 'A'
        val corrupted = listOf(parts[0], parts[1], parts[2], parts[3], corruptedFirst + body.substring(1))
            .joinToString(":")

        assertNull(encoder.decode(corrupted))
    }

    @Test
    fun decode_truncatedBody_returnsNull() {
        val payload = encoder.encode(draft())
        val parts = payload.split(":", limit = 5)
        // Cut into the compressed data, not just the trailing checksum, so the stream cannot inflate.
        val truncated = listOf(parts[0], parts[1], parts[2], parts[3], parts[4].take(parts[4].length / 2))
            .joinToString(":")

        assertNull(encoder.decode(truncated))
    }

    @Test
    fun decode_txHashNotThirtyTwoBytes_returnsNull() {
        val payload = encoder.encode(draft(txHash = "abcd"))

        assertNull(encoder.decode(payload))
    }

    @Test
    fun decode_blankToAddress_returnsNull() {
        val payload = encoder.encode(draft(toAddress = ""))

        assertNull(encoder.decode(payload))
    }

    @Test
    fun decode_negativeAmount_returnsNull() {
        val payload = encoder.encode(draft(amount = BigDecimal("-0.001")))

        assertNull(encoder.decode(payload))
    }

    @Test
    fun decode_oldBodyWithoutToken_returnsNull() {
        val rawHex = "deadbeefdeadbeef"
        val oldBody = """
            {
              "version":1,
              "blockchainUid":"bitcoin",
              "encoding":"rawhex",
              "rawHex":"$rawHex",
              "txHash":"$TX_HASH",
              "amountAtomic":"100000",
              "feeAtomic":"1000",
              "toAddress":"bc1qexampleaddrxyz",
              "createdAt":$CREATED_AT,
              "inputOutpoints":[],
              "checksum":"${checksum(rawHex)}"
            }
        """.trimIndent()
        val payload = "pcash:tx:v1:bitcoin:${compressedBase64(oldBody)}"

        assertNull(encoder.decode(payload))
    }

    @Test
    fun decode_malformedTokenQueryId_returnsNull() {
        val payload = payloadFromBody(validBody(tokenQueryId = "not-a-token-query"))

        assertNull(encoder.decode(payload))
    }

    @Test
    fun decode_tokenBlockchainMismatch_returnsNull() {
        val payload = payloadFromBody(validBody(tokenQueryId = "ethereum|native"))

        assertNull(encoder.decode(payload))
    }

    @Test
    fun decode_feeBlockchainMismatch_returnsNull() {
        val payload = payloadFromBody(validBody(feeTokenQueryId = "ethereum|native"))

        assertNull(encoder.decode(payload))
    }

    @Test
    fun decode_solanaRoundTrip_returnsRetryMetadataAndPreservesSignatureCase() {
        val payload = encoder.encode(
            draft(
                txHash = SOLANA_SIGNATURE,
                token = solanaToken(),
                feeToken = solanaToken(),
                solanaRetryMetadata = solanaRetryMetadata(),
            )
        )

        val decoded = requireNotNull(encoder.decode(payload))

        assertEquals("solana", decoded.blockchainUid)
        assertEquals(SOLANA_SIGNATURE, decoded.txHash)
        assertEquals("solana|native", decoded.token.tokenQueryId)
        assertEquals("block-hash", decoded.solanaRetryMetadata?.blockHash)
        assertEquals(123L, decoded.solanaRetryMetadata?.lastValidBlockHeight)
    }

    @Test
    fun decode_solanaPayloadWithoutRetryMetadata_returnsNull() {
        val payload = encoder.encode(
            draft(
                txHash = SOLANA_SIGNATURE,
                token = solanaToken(),
                feeToken = solanaToken(),
            )
        )

        assertNull(encoder.decode(payload))
    }

    @Test
    fun decode_nonSolanaPayloadWithSolanaRetryMetadata_returnsNull() {
        val payload = encoder.encode(draft(solanaRetryMetadata = solanaRetryMetadata()))

        assertNull(encoder.decode(payload))
    }

    @Test
    fun decode_solanaInvalidSignature_returnsNull() {
        val payload = encoder.encode(
            draft(
                txHash = "0123456789abcdef",
                token = solanaToken(),
                feeToken = solanaToken(),
                solanaRetryMetadata = solanaRetryMetadata(),
            )
        )

        assertNull(encoder.decode(payload))
    }

    @Test
    fun decode_tonRoundTrip_returnsRetryMetadataAndHexHash() {
        val payload = encoder.encode(
            draft(
                token = tonToken(),
                feeToken = tonToken(),
                tonRetryMetadata = tonRetryMetadata(),
            )
        )

        val decoded = requireNotNull(encoder.decode(payload))

        assertEquals("the-open-network", decoded.blockchainUid)
        assertEquals(TX_HASH, decoded.txHash)
        assertEquals("the-open-network|native", decoded.token.tokenQueryId)
        assertEquals(1_700_000_300L, decoded.tonRetryMetadata?.validUntil)
        assertEquals("EQSender", decoded.tonRetryMetadata?.senderAddress)
        assertEquals(7, decoded.tonRetryMetadata?.seqno)
    }

    @Test
    fun decode_tonPayloadWithoutRetryMetadata_returnsNull() {
        val payload = encoder.encode(
            draft(
                token = tonToken(),
                feeToken = tonToken(),
            )
        )

        assertNull(encoder.decode(payload))
    }

    @Test
    fun decode_nonTonPayloadWithTonRetryMetadata_returnsNull() {
        val payload = encoder.encode(draft(tonRetryMetadata = tonRetryMetadata()))

        assertNull(encoder.decode(payload))
    }

    @Test
    fun decode_tonInvalidRetryMetadata_returnsNull() {
        val payload = encoder.encode(
            draft(
                token = tonToken(),
                feeToken = tonToken(),
                tonRetryMetadata = tonRetryMetadata(validUntil = 0),
            )
        )

        assertNull(encoder.decode(payload))
    }

    @Test
    fun decode_tonJettonRoundTrip_preservesDisplayTokenAndNativeFee() {
        val payload = encoder.encode(
            draft(
                token = jettonToken(),
                feeToken = tonToken(),
                amount = BigDecimal("12.345678"),
                fee = BigDecimal("0.000000001"),
                tonRetryMetadata = tonRetryMetadata(),
            )
        )

        val decoded = requireNotNull(encoder.decode(payload))

        assertEquals("the-open-network|the-open-network:EQJetton", decoded.token.tokenQueryId)
        assertEquals("JET", decoded.token.coinCode)
        assertEquals(6, decoded.token.decimals)
        assertEquals("12345678", decoded.amountAtomic)
        assertEquals("the-open-network|native", decoded.fee?.tokenQueryId)
        assertEquals(9, decoded.fee?.decimals)
        assertEquals("1", decoded.fee?.atomic)
    }

    @Test
    fun decode_tronRoundTrip_returnsRetryMetadataAndHexHash() {
        val payload = encoder.encode(
            draft(
                token = tronToken(),
                feeToken = tronToken(),
                tronRetryMetadata = tronRetryMetadata(),
            )
        )

        val decoded = requireNotNull(encoder.decode(payload))

        assertEquals("tron", decoded.blockchainUid)
        assertEquals(TX_HASH, decoded.txHash)
        assertEquals("tron|native", decoded.token.tokenQueryId)
        assertEquals(1_700_000_060_000L, decoded.tronRetryMetadata?.expiration)
    }

    @Test
    fun decode_tronPayloadWithoutRetryMetadata_returnsNull() {
        val payload = encoder.encode(
            draft(
                token = tronToken(),
                feeToken = tronToken(),
            )
        )

        assertNull(encoder.decode(payload))
    }

    @Test
    fun decode_nonTronPayloadWithTronRetryMetadata_returnsNull() {
        val payload = encoder.encode(draft(tronRetryMetadata = tronRetryMetadata()))

        assertNull(encoder.decode(payload))
    }

    @Test
    fun decode_tronInvalidRetryMetadata_returnsNull() {
        val payload = encoder.encode(
            draft(
                token = tronToken(),
                feeToken = tronToken(),
                tronRetryMetadata = tronRetryMetadata(expiration = 0),
            )
        )

        assertNull(encoder.decode(payload))
    }

    @Test
    fun decode_stellarRoundTrip_returnsRetryMetadataAndHexHash() {
        val payload = encoder.encode(
            draft(
                token = stellarToken(),
                feeToken = stellarToken(),
                stellarRetryMetadata = stellarRetryMetadata(),
            )
        )

        val decoded = requireNotNull(encoder.decode(payload))

        assertEquals("stellar", decoded.blockchainUid)
        assertEquals(TX_HASH, decoded.txHash)
        assertEquals("stellar|native", decoded.token.tokenQueryId)
        assertEquals("GSource", decoded.stellarRetryMetadata?.sourceAccountId)
        assertEquals(123_456_789L, decoded.stellarRetryMetadata?.sequenceNumber)
        assertEquals(1_700_000_180L, decoded.stellarRetryMetadata?.validUntil)
    }

    @Test
    fun decode_stellarPayloadWithoutRetryMetadata_returnsNull() {
        val payload = encoder.encode(
            draft(
                token = stellarToken(),
                feeToken = stellarToken(),
            )
        )

        assertNull(encoder.decode(payload))
    }

    @Test
    fun decode_nonStellarPayloadWithStellarRetryMetadata_returnsNull() {
        val payload = encoder.encode(draft(stellarRetryMetadata = stellarRetryMetadata()))

        assertNull(encoder.decode(payload))
    }

    @Test
    fun decode_stellarInvalidRetryMetadata_returnsNull() {
        val payload = encoder.encode(
            draft(
                token = stellarToken(),
                feeToken = stellarToken(),
                stellarRetryMetadata = stellarRetryMetadata(validUntil = 0),
            )
        )

        assertNull(encoder.decode(payload))
    }

    @Test
    fun decode_moneroRoundTrip_returnsNativeMetadataWithoutRetryMetadata() {
        val payload = encoder.encode(
            draft(
                token = moneroToken(),
                feeToken = moneroToken(),
            )
        )

        val decoded = requireNotNull(encoder.decode(payload))

        assertEquals("monero", decoded.blockchainUid)
        assertEquals(TX_HASH, decoded.txHash)
        assertEquals("monero|native", decoded.token.tokenQueryId)
        assertEquals("XMR", decoded.token.coinCode)
        assertEquals(12, decoded.token.decimals)
        assertNull(decoded.solanaRetryMetadata)
        assertNull(decoded.tonRetryMetadata)
        assertNull(decoded.tronRetryMetadata)
        assertNull(decoded.stellarRetryMetadata)
    }

    @Test
    fun decode_moneroPayloadWithForeignRetryMetadata_returnsNull() {
        val payload = encoder.encode(
            draft(
                token = moneroToken(),
                feeToken = moneroToken(),
                stellarRetryMetadata = stellarRetryMetadata(),
            )
        )

        assertNull(encoder.decode(payload))
    }

    @Test
    fun decode_zcashRoundTrip_returnsAddressSpecMetadataWithoutRetryMetadata() {
        val payload = encoder.encode(
            draft(
                token = zcashToken(),
                feeToken = zcashToken(),
            )
        )

        val decoded = requireNotNull(encoder.decode(payload))

        assertEquals("zcash", decoded.blockchainUid)
        assertEquals(TX_HASH, decoded.txHash)
        assertEquals("zcash|address_spec_type:shielded", decoded.token.tokenQueryId)
        assertEquals("ZEC", decoded.token.coinCode)
        assertEquals(8, decoded.token.decimals)
        assertNull(decoded.solanaRetryMetadata)
        assertNull(decoded.tonRetryMetadata)
        assertNull(decoded.tronRetryMetadata)
        assertNull(decoded.stellarRetryMetadata)
    }

    @Test
    fun decode_zcashPayloadWithForeignRetryMetadata_returnsNull() {
        val payload = encoder.encode(
            draft(
                token = zcashToken(),
                feeToken = zcashToken(),
                stellarRetryMetadata = stellarRetryMetadata(),
            )
        )

        assertNull(encoder.decode(payload))
    }

    @Test
    fun decode_zcashPayloadWithInvalidTxHash_returnsNull() {
        val payload = encoder.encode(
            draft(
                token = zcashToken(),
                feeToken = zcashToken(),
                txHash = "not-a-valid-hash",
            )
        )

        assertNull(encoder.decode(payload))
    }

    @Test
    fun isOfflineTransactionPayload_matchesOnlyPcashPrefix() {
        assertTrue(OfflineTransactionPayloadEncoder.isOfflineTransactionPayload("pcash:tx:v1:bitcoin:body"))
        assertTrue(OfflineTransactionPayloadEncoder.isOfflineTransactionPayload("  pcash:tx:anything"))
        assertFalse(OfflineTransactionPayloadEncoder.isOfflineTransactionPayload("deadbeef"))
        assertFalse(OfflineTransactionPayloadEncoder.isOfflineTransactionPayload("bitcoin:tx:v1"))
    }

    @Test
    fun isRawTransactionHex_acceptsOnlyLongEvenHex() {
        assertTrue(OfflineTransactionPayloadEncoder.isRawTransactionHex("deadbeefdeadbeefdead"))
        assertTrue(OfflineTransactionPayloadEncoder.isRawTransactionHex("  DEADBEEFDEADBEEFDEAD  "))
        assertFalse(OfflineTransactionPayloadEncoder.isRawTransactionHex("deadbeef"))
        assertFalse(OfflineTransactionPayloadEncoder.isRawTransactionHex("deadbeefdeadbeefdea"))
        assertFalse(OfflineTransactionPayloadEncoder.isRawTransactionHex("deadbeefdeadbeefzzzz"))
        assertFalse(OfflineTransactionPayloadEncoder.isRawTransactionHex("pcash:tx:v1:bitcoin:body"))
    }

    @Test
    fun decode_beamRoundTrip_keepsKernelAndCoreTxIdSeparate() {
        for (network in listOf("mainnet", "testnet")) {
            for (coreTxId in listOf(null, "ab".repeat(16))) {
                val metadata = beamMetadata().copy(network = network, coreTxId = coreTxId)
                // Same shape as BeamOfflineOperations.export: the receiver is confidential and left blank.
                val payload = encoder.encode(draft(toAddress = "", token = beamToken(), beamMetadata = metadata))
                val decoded = requireNotNull(encoder.decode(payload))

                assertTrue(payload.startsWith("pcash:tx:v1:beam:"))
                assertEquals("", decoded.toAddress)
                assertEquals(metadata, decoded.beamMetadata)
                assertEquals(metadata.mainKernelId, decoded.txHash)
                assertEquals("beam|native", decoded.token.tokenQueryId)
                assertEquals(8, decoded.token.decimals)
                assertEquals("100000", decoded.amountAtomic)
            }
        }
    }

    @Test
    fun decode_legacyChainRoundTrips_remainCompatibleWithoutBeamExtension() {
        val chains = listOf(
            BlockchainType.Bitcoin, BlockchainType.BitcoinCash, BlockchainType.ECash,
            BlockchainType.Litecoin, BlockchainType.Dogecoin, BlockchainType.Dash,
            BlockchainType.Ethereum, BlockchainType.BinanceSmartChain, BlockchainType.Polygon,
            BlockchainType.Avalanche, BlockchainType.Optimism, BlockchainType.ArbitrumOne,
            BlockchainType.Gnosis, BlockchainType.Fantom, BlockchainType.Base,
            BlockchainType.Cosanta, BlockchainType.PirateCash, BlockchainType.ZkSync,
            BlockchainType.RobinhoodChain,
        )
        chains.forEach { chain ->
            val token = token(chain, chain.uid, Coin(chain.uid, chain.uid, "COIN"), decimals = 8)
            val decoded = requireNotNull(encoder.decode(encoder.encode(draft(token = token))))
            assertEquals(chain.uid, decoded.blockchainUid)
            assertEquals(TX_HASH, decoded.txHash)
            assertNull(decoded.beamMetadata)
        }
        assertNotNull(encoder.decode(payloadFromBody(validBody())))
    }

    @Test
    fun decode_unknownOptionalFields_preservesV1Compatibility() {
        listOf("bitcoin" to validBody(), "beam" to beamBody()).forEach { (chain, body) ->
            val extended = bodyWithField("future", JsonObject(mapOf("flag" to JsonPrimitive(true))), body)
            assertNotNull(encoder.decode(payloadFromBody(extended, chain)))
        }
        val nested = bodyWithField("beam.future", JsonPrimitive("ignored"))
        assertNotNull(encoder.decode(payloadFromBody(nested, "beam")))
    }

    @Test
    fun decode_beamInvalidMetadata_rejectsStructurally() {
        val invalidFields = listOf(
            "beam" to JsonNull,
            "beam" to JsonPrimitive("invalid"),
            "beam.version" to JsonPrimitive(0),
            "beam.version" to JsonPrimitive(2),
            "beam.network" to JsonPrimitive(""),
            "beam.network" to JsonPrimitive("Mainnet"),
            "beam.network" to JsonPrimitive("unknown"),
            "beam.rulesSignature" to JsonPrimitive(""),
            "beam.rulesSignature" to JsonPrimitive(" \n\t"),
            "beam.rulesSignature" to JsonPrimitive("rules\u0000"),
            "beam.rulesSignature" to JsonPrimitive("rules\u007f"),
            "beam.rulesSignature" to JsonPrimitive("é"),
            "beam.rulesSignature" to JsonPrimitive("a".repeat(2049)),
        )
        assertInvalidBeamFields(invalidFields)
    }

    @Test
    fun decode_beamInvalidKernelOrCoreTxId_rejectsStructurally() {
        assertInvalidBeamFields(listOf(
            "beam.mainKernelId" to JsonPrimitive(TX_HASH.uppercase()),
            "beam.mainKernelId" to JsonPrimitive("ab".repeat(16)),
            "beam.mainKernelId" to JsonPrimitive("ab".repeat(32)),
            "beam.mainKernelId" to JsonPrimitive("z".repeat(64)),
            "beam.coreTxId" to JsonPrimitive("AB".repeat(16)),
            "beam.coreTxId" to JsonPrimitive("z".repeat(32)),
            "beam.coreTxId" to JsonPrimitive(TX_HASH),
            "beam.coreTxId" to JsonPrimitive(""),
            "txHash" to JsonPrimitive("ab".repeat(16)),
        ))
    }

    @Test
    fun decode_beamInvalidNativeIdentityAndAmounts_rejectsStructurally() {
        assertInvalidBeamFields(listOf(
            "blockchainUid" to JsonPrimitive("beam-2"),
            "token.tokenQueryId" to JsonPrimitive("beam-2|native"),
            "token.tokenQueryId" to JsonPrimitive("beam|eip20:asset"),
            "token.decimals" to JsonPrimitive(9),
            "fee.tokenQueryId" to JsonPrimitive("ethereum|native"),
            "fee.decimals" to JsonPrimitive(7),
            "fee.atomic" to JsonPrimitive("9223372036854775808"),
            "amountAtomic" to JsonPrimitive("9223372036854775808"),
            "amountAtomic" to JsonPrimitive("-1"),
            "amountAtomic" to JsonPrimitive("1.5"),
            "amountAtomic" to JsonPrimitive("+1"),
            "rawHex" to JsonPrimitive("DEADBEEFDEADBEEF"),
            "rawHex" to JsonPrimitive("abc"),
        ))
    }

    @Test
    fun decode_beamMissingRequiredFields_rejectsStructurally() {
        val root = Json.parseToJsonElement(beamBody()).jsonObject
        val metadata = root.getValue("beam").jsonObject
        for (field in listOf("version", "network", "rulesSignature", "mainKernelId")) {
            val body = bodyWithField("beam", JsonObject(metadata - field))
            assertEquals(field, DecodeResult.Invalid, encoder.decodeResult(payloadFromBody(body, "beam")))
        }
        assertNull(encoder.decode(payloadFromBody(JsonObject(root - "beam").toString(), "beam")))
    }

    @Test
    fun decode_beamRulesAndAtomicBoundaries_acceptsStructurally() {
        val body = bodyWithField("beam.rulesSignature", JsonPrimitive("a".repeat(2046) + "\n\t"))
        val changed = bodyWithField("amountAtomic", JsonPrimitive(Long.MAX_VALUE.toString()), body)
        assertNotNull(encoder.decode(payloadFromBody(changed, "beam")))
    }

    @Test
    fun encode_foreignOrMissingBeamMetadata_rejectsStructurally() {
        assertThrows(IllegalArgumentException::class.java) { encoder.encode(draft(beamMetadata = beamMetadata())) }
        assertThrows(IllegalArgumentException::class.java) { encoder.encode(draft(token = beamToken())) }
        val body = bodyWithField("beam", Json.parseToJsonElement(Json.encodeToString(beamMetadata())), validBody())
        assertEquals(DecodeResult.Invalid, encoder.decodeResult(payloadFromBody(body)))
    }

    @Test
    fun encode_beamFractionalAtomicOrOverflow_rejectsWithoutTruncation() {
        for (amount in listOf(BigDecimal("0.000000001"), BigDecimal("92233720368.54775808"))) {
            val draft = draft(token = beamToken(), beamMetadata = beamMetadata())
            assertThrows(ArithmeticException::class.java) { encoder.encode(draft.copy(amount = amount)) }
            assertThrows(ArithmeticException::class.java) { encoder.encode(draft.copy(fee = amount)) }
        }
    }

    @Test
    fun decode_displayMetadataChanges_doNotBecomeCryptographicProof() {
        val body = bodyWithField("toAddress", JsonPrimitive("unverified receiver"))
        val amount = bodyWithField("amountAtomic", JsonPrimitive("42"), body)
        val changed = bodyWithField("createdAt", JsonPrimitive(1L), amount)
        val decoded = requireNotNull(encoder.decode(payloadFromBody(changed, "beam")))
        assertEquals("42", decoded.amountAtomic)
        assertEquals("unverified receiver", decoded.toAddress)
        assertEquals("deadbeefdeadbeef", decoded.rawHex)
        assertEquals(1L, decoded.createdAt)
    }

    @Test
    fun decodeResult_recognizedMalformedEnvelope_neverReturnsNotEnvelope() {
        listOf("pcash:tx:", "pcash:tx:v1", "pcash:tx:v2:bitcoin:body", " pcash:tx:v1:bitcoin:@@ ")
            .forEach { assertEquals(DecodeResult.Invalid, encoder.decodeResult(it)) }
        assertEquals(DecodeResult.NotEnvelope, encoder.decodeResult("deadbeefdeadbeefdead"))
        assertTrue(encoder.decodeResult(payloadFromBody(validBody())) is DecodeResult.Decoded)
    }

    @Test
    fun decodeResult_oversizedInput_rejectsBeforeBase64Allocation() {
        val oversized = "a".repeat(OfflineTransactionPayloadEncoder.MAX_INPUT_CHARACTERS + 1)
        assertEquals(DecodeResult.Invalid, encoder.decodeResult("pcash:tx:v1:bitcoin:$oversized"))
        assertEquals(DecodeResult.Invalid, encoder.decodeResult(oversized))
        assertFalse(OfflineTransactionPayloadEncoder.isRawTransactionHex(oversized + "a"))
        verify(exactly = 0) { Base64.decode(any<String>(), any()) }
    }

    @Test
    fun encode_oversizedRawHex_rejectsBeforeChecksumOrHexNormalization() {
        val oversized = "A".repeat(OfflineTransactionPayloadEncoder.MAX_INPUT_CHARACTERS + 1)
        assertThrows(IllegalArgumentException::class.java) { encoder.encode(draft(rawHex = oversized)) }
        verify(exactly = 0) { Base64.encodeToString(any(), any()) }
    }

    @Test
    fun decode_compressionBomb_rejectsAtExistingOneMiBLimit() {
        val body = bodyWithField("future", JsonPrimitive("a".repeat(1024 * 1024)), validBody())
        assertEquals(DecodeResult.Invalid, encoder.decodeResult(payloadFromBody(body)))
    }

    @Test
    fun decode_inflatedSizeBoundary_acceptsOneMiBRejectsOneByteMore() {
        val body = validBody().padEnd(1024 * 1024, ' ')
        assertNotNull(encoder.decode(payloadFromBody(body)))
        assertEquals(DecodeResult.Invalid, encoder.decodeResult(payloadFromBody(body + " ")))
    }

    @Test
    fun decode_trailingOrUnfinishedZlibStream_rejectsEvenWithCompleteJson() {
        val compressed = JavaBase64.getUrlDecoder().decode(compressedBase64(validBody()))
        val invalid = listOf(
            compressed + byteArrayOf(0),
            compressed + compressed,
            compressed.copyOf(compressed.size - 1),
            compressed.copyOf(compressed.size - 4),
            compressed.copyOf(compressed.size / 2),
        )
        invalid.forEach { bytes ->
            val body = JavaBase64.getUrlEncoder().withoutPadding().encodeToString(bytes)
            assertEquals(DecodeResult.Invalid, encoder.decodeResult("pcash:tx:v1:bitcoin:$body"))
        }
    }

    @Test
    fun decode_zlibRequiresDictionary_rejectsWithoutLooping() {
        val body = compressedBase64(validBody(), dictionary = "bitcoin rawhex token".encodeToByteArray())
        assertEquals(DecodeResult.Invalid, encoder.decodeResult("pcash:tx:v1:bitcoin:$body"))
    }

    @Test
    fun decode_base64WithTrailingPunctuation_rejectsBeforePermissiveAndroidDecoder() {
        val payload = payloadFromBody(validBody()) + "!"
        assertEquals(DecodeResult.Invalid, encoder.decodeResult(payload))
        verify(exactly = 0) { Base64.decode(any<String>(), any()) }
    }

    private fun assertInvalidBeamFields(fields: List<Pair<String, JsonElement>>) {
        fields.forEach { (path, value) ->
            val payload = payloadFromBody(bodyWithField(path, value), "beam")
            assertEquals(path, DecodeResult.Invalid, encoder.decodeResult(payload))
        }
    }

    private fun bodyWithField(path: String, value: JsonElement, body: String = beamBody()): String {
        val fields = Json.parseToJsonElement(body).jsonObject.toMutableMap()
        val parts = path.split('.', limit = 2)
        fields[parts[0]] = if (parts.size == 1) value else {
            JsonObject(fields.getValue(parts[0]).jsonObject + (parts[1] to value))
        }
        return JsonObject(fields).toString()
    }

    private fun beamBody(): String {
        val body = validBody("beam|native", "beam|native", "beam")
        return bodyWithField("beam", Json.parseToJsonElement(Json.encodeToString(beamMetadata())), body)
    }

    private fun beamMetadata() = OfflineBeamMetadata(
        version = 1,
        network = "mainnet",
        rulesSignature = "synthetic structural fixture; not SDK-validated",
        mainKernelId = TX_HASH,
        coreTxId = "ab".repeat(16),
    )

    private fun beamToken() = token(
        blockchainType = BlockchainType.Beam,
        blockchainName = "Beam",
        coin = Coin(uid = "beam", name = "BEAM", code = "BEAM"),
        decimals = 8,
    )

    private fun draft(
        rawHex: String = "deadbeefdeadbeef",
        txHash: String = TX_HASH,
        toAddress: String = "bc1qexampleaddrxyz",
        amount: BigDecimal = BigDecimal("0.001"),
        fee: BigDecimal = BigDecimal("0.00001"),
        token: Token = token(
            blockchainType = BlockchainType.Bitcoin,
            blockchainName = "Bitcoin",
            coin = Coin(uid = "bitcoin", name = "Bitcoin", code = "BTC"),
            decimals = 8,
        ),
        feeToken: Token? = null,
        solanaRetryMetadata: OfflineSolanaRetryMetadata? = null,
        tonRetryMetadata: OfflineTonRetryMetadata? = null,
        tronRetryMetadata: OfflineTronRetryMetadata? = null,
        stellarRetryMetadata: OfflineStellarRetryMetadata? = null,
        beamMetadata: OfflineBeamMetadata? = null,
    ): OfflineSignedTransactionDraft {
        val wallet = mockk<Wallet>(relaxed = true) {
            every { this@mockk.token } returns token
        }
        return OfflineSignedTransactionDraft(
            wallet = wallet,
            amount = amount,
            fee = fee,
            toAddress = toAddress,
            rawHex = rawHex,
            txHash = txHash,
            inputOutpoints = emptyList(),
            createdAt = CREATED_AT,
            feeToken = feeToken,
            solanaRetryMetadata = solanaRetryMetadata,
            tonRetryMetadata = tonRetryMetadata,
            tronRetryMetadata = tronRetryMetadata,
            stellarRetryMetadata = stellarRetryMetadata,
            beamMetadata = beamMetadata,
        )
    }

    private fun token(
        blockchainType: BlockchainType,
        blockchainName: String,
        coin: Coin,
        tokenType: TokenType = TokenType.Native,
        decimals: Int,
    ) = Token(
        coin = coin,
        blockchain = Blockchain(blockchainType, blockchainName, null),
        type = tokenType,
        decimals = decimals,
    )

    private fun solanaToken() = token(
        blockchainType = BlockchainType.Solana,
        blockchainName = "Solana",
        coin = Coin(uid = "solana", name = "Solana", code = "SOL"),
        decimals = 9,
    )

    private fun solanaRetryMetadata() = OfflineSolanaRetryMetadata(
        blockHash = "block-hash",
        lastValidBlockHeight = 123L,
    )

    private fun tonToken() = token(
        blockchainType = BlockchainType.Ton,
        blockchainName = "TON",
        coin = Coin(uid = "toncoin", name = "Toncoin", code = "TON"),
        decimals = 9,
    )

    private fun jettonToken() = token(
        blockchainType = BlockchainType.Ton,
        blockchainName = "TON",
        coin = Coin(uid = "jetton", name = "Jetton", code = "JET"),
        tokenType = TokenType.Jetton("EQJetton"),
        decimals = 6,
    )

    private fun tonRetryMetadata(
        validUntil: Long = 1_700_000_300L,
        senderAddress: String = "EQSender",
        seqno: Int = 7,
    ) = OfflineTonRetryMetadata(
        validUntil = validUntil,
        senderAddress = senderAddress,
        seqno = seqno,
    )

    private fun tronToken() = token(
        blockchainType = BlockchainType.Tron,
        blockchainName = "Tron",
        coin = Coin(uid = "tron", name = "TRON", code = "TRX"),
        decimals = 6,
    )

    private fun tronRetryMetadata(
        expiration: Long = 1_700_000_060_000L,
    ) = OfflineTronRetryMetadata(
        expiration = expiration,
    )

    private fun stellarToken() = token(
        blockchainType = BlockchainType.Stellar,
        blockchainName = "Stellar",
        coin = Coin(uid = "stellar", name = "Stellar", code = "XLM"),
        decimals = 7,
    )

    private fun stellarRetryMetadata(
        sourceAccountId: String = "GSource",
        sequenceNumber: Long = 123_456_789L,
        validUntil: Long = 1_700_000_180L,
    ) = OfflineStellarRetryMetadata(
        sourceAccountId = sourceAccountId,
        sequenceNumber = sequenceNumber,
        validUntil = validUntil,
    )

    private fun moneroToken() = token(
        blockchainType = BlockchainType.Monero,
        blockchainName = "Monero",
        coin = Coin(uid = "monero", name = "Monero", code = "XMR"),
        decimals = 12,
    )

    private fun zcashToken() = token(
        blockchainType = BlockchainType.Zcash,
        blockchainName = "Zcash",
        coin = Coin(uid = "zcash", name = "Zcash", code = "ZEC"),
        tokenType = TokenType.AddressSpecTyped(TokenType.AddressSpecType.Shielded),
        decimals = 8,
    )

    private fun compressedBase64(json: String, dictionary: ByteArray? = null): String {
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        return try {
            dictionary?.let(deflater::setDictionary)
            deflater.setInput(json.encodeToByteArray())
            deflater.finish()
            val output = ByteArrayOutputStream(json.length)
            val buffer = ByteArray(512)
            while (!deflater.finished()) {
                output.write(buffer, 0, deflater.deflate(buffer))
            }
            JavaBase64.getUrlEncoder().withoutPadding().encodeToString(output.toByteArray())
        } finally {
            deflater.end()
        }
    }

    private fun payloadFromBody(
        body: String,
        blockchainUid: String = "bitcoin",
    ) = "pcash:tx:v1:$blockchainUid:${compressedBase64(body)}"

    private fun validBody(
        tokenQueryId: String = "bitcoin|native",
        feeTokenQueryId: String = "bitcoin|native",
        blockchainUid: String = "bitcoin",
    ): String {
        val rawHex = "deadbeefdeadbeef"
        return """
            {
              "version":1,
              "blockchainUid":"$blockchainUid",
              "encoding":"rawhex",
              "rawHex":"$rawHex",
              "txHash":"$TX_HASH",
              "token":{
                "tokenQueryId":"$tokenQueryId",
                "coinUid":"bitcoin",
                "coinCode":"BTC",
                "coinName":"Bitcoin",
                "decimals":8
              },
              "amountAtomic":"100000",
              "fee":{
                "tokenQueryId":"$feeTokenQueryId",
                "atomic":"1000",
                "decimals":8
              },
              "toAddress":"bc1qexampleaddrxyz",
              "createdAt":$CREATED_AT,
              "inputOutpoints":[],
              "checksum":"${checksum(rawHex)}"
            }
        """.trimIndent()
    }

    private fun checksum(rawHex: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(rawHex.lowercase().encodeToByteArray())
        return JavaBase64.getUrlEncoder().withoutPadding().encodeToString(digest.copyOf(8))
    }

    private companion object {
        const val TX_HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        val SOLANA_SIGNATURE = Base58.encode(ByteArray(64) { (it + 1).toByte() })
        const val CREATED_AT = 1_700_000_000_000L
    }
}
