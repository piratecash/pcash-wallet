package cash.p.terminal.tangem.domain

import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TangemConfigBeamTest {
    @Test
    fun nativeBeamIsExcludedWithoutChangingGameTokenChains() {
        assertTrue(TangemConfig.isExcludedForHardwareCard(BlockchainType.Beam, TokenType.Native))
        assertFalse(TangemConfig.isExcludedForHardwareCard(BlockchainType.Ethereum, TokenType.Native))
        assertFalse(TangemConfig.isExcludedForHardwareCard(BlockchainType.BinanceSmartChain, TokenType.Native))
    }
}
