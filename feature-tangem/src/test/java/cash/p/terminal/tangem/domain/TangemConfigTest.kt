package cash.p.terminal.tangem.domain

import cash.p.terminal.wallet.entities.TokenType
import com.tangem.common.card.EllipticCurve
import io.horizontalsystems.core.entities.BlockchainType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TangemConfigTest {

    @Test
    fun isExcludedForHardwareCard_thorchainAndMayachainNative_returnsTrue() {
        assertTrue(TangemConfig.isExcludedForHardwareCard(BlockchainType.Thorchain, TokenType.Native))
        assertTrue(TangemConfig.isExcludedForHardwareCard(BlockchainType.Mayachain, TokenType.Native))
    }

    @Test
    fun getSupportedCurves_thorchainAndMayachain_returnsEmptyList() {
        assertEquals(emptyList<EllipticCurve>(), BlockchainType.Thorchain.getSupportedCurves())
        assertEquals(emptyList<EllipticCurve>(), BlockchainType.Mayachain.getSupportedCurves())
    }
}
