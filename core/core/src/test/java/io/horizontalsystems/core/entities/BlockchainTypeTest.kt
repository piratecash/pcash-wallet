package io.horizontalsystems.core.entities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class BlockchainTypeTest {

    @Test
    fun robinhoodChain_identifiers_matchBackendContract() {
        assertEquals("robinhood", BlockchainType.RobinhoodChain.uid)
        assertEquals("robinhoodChain", BlockchainType.RobinhoodChain.stringRepresentation)
        assertEquals("robinhoodChain", BlockchainType.RobinhoodChain.toString())
        assertSame(BlockchainType.RobinhoodChain, BlockchainType.fromUid("robinhood"))
    }
}
