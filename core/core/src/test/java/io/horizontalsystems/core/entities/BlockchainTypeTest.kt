package io.horizontalsystems.core.entities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockchainTypeTest {

    @Test
    fun robinhoodChain_identifiers_matchBackendContract() {
        assertEquals("robinhood", BlockchainType.RobinhoodChain.uid)
        assertEquals("robinhoodChain", BlockchainType.RobinhoodChain.stringRepresentation)
        assertEquals("robinhoodChain", BlockchainType.RobinhoodChain.toString())
        assertSame(BlockchainType.RobinhoodChain, BlockchainType.fromUid("robinhood"))
    }

    @Test
    fun fromUid_nativeBeam_resolvesBeamType() {
        assertEquals("beam", BlockchainType.Beam.uid)
        assertEquals("beam", BlockchainType.Beam.stringRepresentation)
        assertSame(BlockchainType.Beam, BlockchainType.fromUid("beam"))
    }

    @Test
    fun fromUid_gameBeam_doesNotResolveNativeBeam() {
        val type = BlockchainType.fromUid("beam-2")

        assertTrue(type is BlockchainType.Unsupported)
        assertEquals("beam-2", type.uid)
    }
}
