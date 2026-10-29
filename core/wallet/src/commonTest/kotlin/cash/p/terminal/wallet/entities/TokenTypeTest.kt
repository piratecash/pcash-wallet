package cash.p.terminal.wallet.entities

import cash.p.terminal.wallet.meta
import cash.p.terminal.wallet.protocolType
import io.horizontalsystems.core.entities.BlockchainType
import kotlin.test.Test
import kotlin.test.assertEquals

class TokenTypeTest {

    @Test
    fun id_mweb_returnsMwebId() {
        assertEquals("mweb", TokenType.Mweb.id)
    }

    @Test
    fun values_mweb_returnsMwebTypeAndEmptyReference() {
        assertEquals(TokenType.Value(type = "mweb", reference = ""), TokenType.Mweb.values)
    }

    @Test
    fun fromType_mweb_returnsMwebTokenType() {
        assertEquals(TokenType.Mweb, TokenType.fromType("mweb"))
    }

    @Test
    fun fromId_mweb_returnsMwebTokenType() {
        assertEquals(TokenType.Mweb, TokenType.fromId("mweb"))
    }

    @Test
    fun fromType_stellarReference_returnsAssetTokenType() {
        assertEquals(
            TokenType.Asset(code = "USDC", issuer = "GDQOE23QKUSLOXFW77F5GBDSYFND5XPF7FJHJ42SRD7Y7RP2T7BDXYCP"),
            TokenType.fromType("stellar", "USDC-GDQOE23QKUSLOXFW77F5GBDSYFND5XPF7FJHJ42SRD7Y7RP2T7BDXYCP")
        )
    }

    @Test
    fun fromId_stellarAsset_returnsAssetTokenType() {
        val tokenType = TokenType.Asset(
            code = "USDC",
            issuer = "GDQOE23QKUSLOXFW77F5GBDSYFND5XPF7FJHJ42SRD7Y7RP2T7BDXYCP"
        )

        assertEquals(tokenType, TokenType.fromId(tokenType.id))
    }

    @Test
    fun fromType_trc10Reference_returnsStableTrc10Id() {
        val tokenType = TokenType.fromType("trc10", "1005114")

        assertEquals("trc10:1005114", tokenType.id)
        assertEquals(TokenType.Value(type = "trc10", reference = "1005114"), tokenType.values)
    }

    @Test
    fun fromId_trc10_returnsTrc10TokenType() {
        assertEquals(TokenType.Trc10("1005114"), TokenType.fromId("trc10:1005114"))
    }

    @Test
    fun fromType_trc10WithoutReference_returnsUnsupportedTokenType() {
        assertEquals(
            TokenType.Unsupported(type = "trc10", reference = ""),
            TokenType.fromType("trc10"),
        )
    }

    @Test
    fun trc10_assetId_returnsTronTokenQuery() {
        assertEquals(
            TokenQuery(BlockchainType.Tron, TokenType.Trc10("1005114")),
            TokenQuery.trc10("1005114"),
        )
    }

    @Test
    fun fromId_trc10TokenQueryId_returnsTrc10TokenQuery() {
        val query = TokenQuery.trc10("1005114")

        assertEquals(query, TokenQuery.fromId(query.id))
    }

    @Test
    fun protocolType_trc10_returnsTrc10Label() {
        assertEquals("TRC10", TokenQuery.trc10("1005114").protocolType)
    }

    @Test
    fun fromType_thorchainSecuredDenom_returnsThorchainAsset() {
        val denom = "eth-usdt-0xdac17f958d2ee523a2206206994597c13d831ec7"

        assertEquals(TokenType.ThorchainAsset(denom), TokenType.fromType("thorchain", denom))
    }

    @Test
    fun id_thorchainAsset_roundTripsThroughFromId() {
        val denom = "eth-usdt-0xdac17f958d2ee523a2206206994597c13d831ec7"
        val tokenType = TokenType.ThorchainAsset(denom)

        assertEquals("thorchain:$denom", tokenType.id)
        assertEquals(tokenType, TokenType.fromId(tokenType.id))
    }

    @Test
    fun values_thorchainAsset_returnsThorchainTypeAndDenomReference() {
        val denom = "eth-usdt-0xdac17f958d2ee523a2206206994597c13d831ec7"

        assertEquals(
            TokenType.Value(type = "thorchain", reference = denom),
            TokenType.ThorchainAsset(denom).values,
        )
    }

    @Test
    fun fromType_thorchainBlankReference_returnsUnsupportedTokenType() {
        assertEquals(
            TokenType.Unsupported(type = "thorchain", reference = ""),
            TokenType.fromType("thorchain"),
        )
    }

    @Test
    fun id_tonW5Native_roundTripsThroughFromId() {
        assertEquals("ton_w5_native", TokenType.TonW5Native.id)
        assertEquals(TokenType.Value(type = "ton_w5_native", reference = ""), TokenType.TonW5Native.values)
        assertEquals(TokenType.TonW5Native, TokenType.fromId("ton_w5_native"))
        assertEquals(TokenType.TonW5Native, TokenType.fromType("ton_w5_native", ""))
    }

    @Test
    fun id_tonW5Jetton_roundTripsThroughFromId() {
        val tokenType = TokenType.TonW5Jetton(JETTON_MASTER)

        assertEquals("ton_w5_jetton:$JETTON_MASTER", tokenType.id)
        assertEquals(TokenType.Value(type = "ton_w5_jetton", reference = JETTON_MASTER), tokenType.values)
        assertEquals(tokenType, TokenType.fromId(tokenType.id))
        assertEquals(tokenType, TokenType.fromType("ton_w5_jetton", JETTON_MASTER))
    }

    @Test
    fun fromType_tonW5JettonBlankReference_returnsUnsupportedTokenType() {
        assertEquals(TokenType.Unsupported(type = "ton_w5_jetton", reference = ""), TokenType.fromType("ton_w5_jetton"))
    }

    @Test
    fun id_tonV4R2Types_unchanged() {
        assertEquals("native", TokenType.Native.id)
        assertEquals("the-open-network:$JETTON_MASTER", TokenType.Jetton(JETTON_MASTER).id)
        assertEquals(TokenType.Jetton(JETTON_MASTER), TokenType.fromId("the-open-network:$JETTON_MASTER"))
    }

    @Test
    fun protocolType_tonAssets_showVersionExceptV4R2Jetton() {
        assertEquals("V4R2", TokenQuery(BlockchainType.Ton, TokenType.Native).protocolType)
        assertEquals("W5", TokenQuery(BlockchainType.Ton, TokenType.TonW5Native).protocolType)
        assertEquals("JETTON", TokenQuery(BlockchainType.Ton, TokenType.Jetton(JETTON_MASTER)).protocolType)
        assertEquals("JETTON W5", TokenQuery(BlockchainType.Ton, TokenType.TonW5Jetton(JETTON_MASTER)).protocolType)
    }

    @Test
    fun meta_tonTypes_w5SeparatedFromV4R2() {
        assertEquals(null, TokenType.Native.meta)
        assertEquals(null, TokenType.Jetton(JETTON_MASTER).meta)
        assertEquals("w5", TokenType.TonW5Native.meta)
        assertEquals("w5", TokenType.TonW5Jetton(JETTON_MASTER).meta)
    }

    private companion object {
        const val JETTON_MASTER = "EQCxE6mUtQJKFnGfaROTKOt1lZbDiiX1kCixRv7Nw2Id_sDs"
    }
}
