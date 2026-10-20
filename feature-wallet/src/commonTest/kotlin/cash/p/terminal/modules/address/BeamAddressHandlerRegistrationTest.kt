package cash.p.terminal.modules.address

import cash.p.terminal.core.supported
import cash.p.terminal.modules.send.beam.BeamRecipient
import io.horizontalsystems.core.entities.BlockchainType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BEAM used to share the `Unsupported` arm of the shared handler factory, so every screen that
 * reads an address through it — the balance QR scanner, the address checker — rejected a valid
 * BEAM token. These cases assert the wiring only: they never invoke a handler, because several
 * of them (BEAM, Monero) reach native code that a JVM unit test cannot load.
 */
class BeamAddressHandlerRegistrationTest {

    private val factory = AddressHandlerFactory(udnApiKey = "unused")

    @Test
    fun `beam contributes its one-sided token handler`() {
        assertSame(BeamRecipient, factory.parserChainHandlers(BlockchainType.Beam).single())
    }

    @Test
    fun `beam is enumerated by the chain-less parser chain`() {
        // parserChain(null) iterates BlockchainType.supported; BEAM was enumerated before this
        // change too, and simply contributed nothing.
        assertTrue(BlockchainType.Beam in BlockchainType.supported)
        assertTrue(factory.parserChainHandlers(BlockchainType.Beam).isNotEmpty())
    }

    @Test
    fun `the other chains keep exactly the handlers they had`() {
        // R13: this change must lift BEAM to the level of the chains that already work, and
        // leave those chains untouched.
        assertEquals(
            listOf(AddressHandlerBase58::class, AddressHandlerBech32::class),
            factory.parserChainHandlers(BlockchainType.Bitcoin).map { it::class },
        )
        assertEquals(
            listOf(AddressHandlerMonero::class),
            factory.parserChainHandlers(BlockchainType.Monero).map { it::class },
        )
        assertEquals(
            listOf(AddressHandlerZcash::class),
            factory.parserChainHandlers(BlockchainType.Zcash).map { it::class },
        )
        assertEquals(
            listOf(AddressHandlerStellar::class),
            factory.parserChainHandlers(BlockchainType.Stellar).map { it::class },
        )
        assertEquals(
            listOf(AddressHandlerEvm::class),
            factory.parserChainHandlers(BlockchainType.Ethereum).map { it::class },
        )
        assertEquals(
            emptyList<Any>(),
            factory.parserChainHandlers(BlockchainType.Unsupported("nonsense")).map { it::class },
        )
    }
}
