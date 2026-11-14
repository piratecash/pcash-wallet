package cash.p.terminal.modules.address

import cash.p.terminal.core.ISendTonAdapter
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.tonkit.FriendlyAddress
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ton.block.AddrStd

class AddressHandlerTonDnsTest {

    private val adapter = mockk<ISendTonAdapter>()
    private val handler = AddressHandlerTonDns(adapter)
    private val resolved = FriendlyAddress(AddrStd(0, ByteArray(32) { 1 }), isBounceable = false)

    @Test
    fun isSupported_tonDomain_trueWithoutNetwork() {
        assertTrue(handler.isSupported("alex.ton"))
        assertTrue(handler.isSupported("Alex.TON"))

        verify(exactly = 0) { adapter.resolveDomain(any()) }
    }

    @Test
    fun isSupported_friendlyAddress_false() {
        assertFalse(handler.isSupported(resolved.addrStd.toString(userFriendly = true)))
        assertFalse(handler.isSupported("alex.eth"))
    }

    @Test
    fun parseAddress_resolvedDomain_returnsFriendlyAddressWithDomain() {
        every { adapter.resolveDomain("alex.ton") } returns resolved

        val address = handler.parseAddress("Alex.ton")

        assertEquals(resolved.addrStd.toString(userFriendly = true, bounceable = false), address.hex)
        assertEquals("alex.ton", address.domain)
        assertEquals(BlockchainType.Ton, address.blockchainType)
    }

    @Test
    fun parseAddress_unresolvedDomain_throwsValidationError() {
        every { adapter.resolveDomain("nobody.ton") } returns null

        assertThrows(AddressValidationException.Invalid::class.java) { handler.parseAddress("nobody.ton") }
    }
}
