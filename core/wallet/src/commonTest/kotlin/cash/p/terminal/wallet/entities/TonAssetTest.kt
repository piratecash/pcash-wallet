package cash.p.terminal.wallet.entities

import io.horizontalsystems.core.entities.BlockchainType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TonAssetTest {

    private val tonTypes = listOf(
        TokenType.Native to TonAsset.Native(TonWalletVersion.V4R2),
        TokenType.TonW5Native to TonAsset.Native(TonWalletVersion.W5),
        TokenType.Jetton(MASTER) to TonAsset.Jetton(MASTER, TonWalletVersion.V4R2),
        TokenType.TonW5Jetton(MASTER) to TonAsset.Jetton(MASTER, TonWalletVersion.W5),
    )

    @Test
    fun tonAsset_tonTokenTypes_classifiedAndMappedBack() {
        tonTypes.forEach { (tokenType, asset) ->
            assertEquals(asset, TokenQuery(BlockchainType.Ton, tokenType).tonAsset)
            assertEquals(tokenType, asset.tokenType)
        }
    }

    @Test
    fun tonAsset_nativeOnOtherChain_null() {
        assertNull(TokenQuery(BlockchainType.Ethereum, TokenType.Native).tonAsset)
    }

    @Test
    fun nativeTokenQuery_w5Jetton_returnsW5Native() {
        assertEquals(
            TokenQuery(BlockchainType.Ton, TokenType.TonW5Native),
            TokenQuery(BlockchainType.Ton, TokenType.TonW5Jetton(MASTER)).nativeTokenQuery,
        )
    }

    @Test
    fun nativeTokenQuery_v4r2Jetton_returnsNative() {
        assertEquals(
            TokenQuery(BlockchainType.Ton, TokenType.Native),
            TokenQuery(BlockchainType.Ton, TokenType.Jetton(MASTER)).nativeTokenQuery,
        )
    }

    @Test
    fun nativeTokenQuery_erc20_returnsChainNative() {
        assertEquals(
            TokenQuery(BlockchainType.Ethereum, TokenType.Native),
            TokenQuery(BlockchainType.Ethereum, TokenType.Eip20("0xtoken")).nativeTokenQuery,
        )
    }

    private companion object {
        const val MASTER = "EQCxE6mUtQJKFnGfaROTKOt1lZbDiiX1kCixRv7Nw2Id_sDs"
    }
}
