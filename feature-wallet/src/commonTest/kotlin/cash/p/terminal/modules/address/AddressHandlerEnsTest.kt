package cash.p.terminal.modules.address

import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.ethereumkit.ens.EnsResolutionError
import io.horizontalsystems.ethereumkit.ens.EnsResolver
import io.horizontalsystems.ethereumkit.models.Address
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Single
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressHandlerEnsTest {

    private val resolver = mockk<EnsResolver>()
    private val handler = AddressHandlerEns(BlockchainType.Ethereum, resolver)
    private val checksummed = "0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045"
    private val resolved = mockk<Address> { every { eip55 } returns checksummed }

    @Test
    fun isSupported_resolverSucceeds_returnsTrueAndParseAddressReturnsResolved() {
        every { resolver.resolve("vitalik.eth") } returns Single.just(resolved)

        assertTrue(handler.isSupported("vitalik.eth"))

        val address = handler.parseAddress("vitalik.eth")
        assertEquals(checksummed, address.hex)
        assertEquals("vitalik.eth", address.domain)
        assertEquals(BlockchainType.Ethereum, address.blockchainType)
    }

    @Test
    fun isSupported_resolverFails_returnsFalse() {
        every { resolver.resolve("missing.eth") } returns Single.error(EnsResolutionError.NotFound())

        assertFalse(handler.isSupported("missing.eth"))
    }

    @Test
    fun isSupported_nameWithoutDot_returnsFalse() {
        every { resolver.resolve("vitalik") } returns Single.error(EnsResolutionError.InvalidName())

        assertFalse(handler.isSupported("vitalik"))
    }
}
