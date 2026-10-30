package cash.p.terminal.modules.multiswap.providers

import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import org.junit.Assert.assertEquals
import org.junit.Test

class SwapProviderTokenUtilsTest {

    @Test
    fun contractAddress_tonW5Tokens_matchV4r2Twins() {
        assertEquals("EQmaster", tonToken(TokenType.TonW5Jetton("EQmaster")).contractAddress())
        assertEquals("", tonToken(TokenType.TonW5Native).contractAddress())
    }

    @Test
    fun swapAssetKind_tonW5Tokens_matchV4r2Twins() {
        assertEquals(SwapAssetKind.NATIVE, tonToken(TokenType.TonW5Native).swapAssetKind)
        assertEquals(SwapAssetKind.CONTRACT, tonToken(TokenType.TonW5Jetton("EQmaster")).swapAssetKind)
    }

    private fun tonToken(type: TokenType) = yiFiTestToken(BlockchainType.Ton, type, "TON")
}
