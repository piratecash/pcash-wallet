package cash.p.terminal.modules.hardwarewallet

import cash.p.terminal.ledger.domain.LedgerConnector
import cash.p.terminal.ledgerkit.device.LedgerModel
import cash.p.terminal.ledgerkit.transport.LedgerTransport
import cash.p.terminal.ledgerkit.transport.SpeculosTransport
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket

class SpeculosSwitchingLedgerConnectorTest {

    private val usbTransport = mockk<LedgerTransport>()
    private val usb = mockk<LedgerConnector> {
        coEvery { open() } returns usbTransport
        every { model } returns LedgerModel.NanoX
    }
    private val settings = mockk<LedgerSpeculosSettings>()

    @Test
    fun open_speculosOff_opensUsbLedger() = runTest {
        every { settings.enabled } returns false

        val transport = SpeculosSwitchingLedgerConnector(usb, settings).open()

        assertSame(usbTransport, transport)
    }

    @Test
    fun open_speculosOn_connectsToConfiguredEmulator() = runTest {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { emulator ->
            every { settings.enabled } returns true
            every { settings.host } returns emulator.inetAddress.hostAddress
            every { settings.port } returns emulator.localPort

            val transport = SpeculosSwitchingLedgerConnector(usb, settings).open()

            assertTrue(transport is SpeculosTransport)
            transport.close()
        }
        coVerify(exactly = 0) { usb.open() }
    }

    @Test
    fun isDevicePresent_speculosOnUsbAbsent_isTrue() {
        every { settings.enabled } returns true
        every { usb.isDevicePresent } returns false

        assertTrue(SpeculosSwitchingLedgerConnector(usb, settings).isDevicePresent)
    }

    @Test
    fun isDevicePresent_speculosOff_followsUsb() {
        every { settings.enabled } returns false
        val connector = SpeculosSwitchingLedgerConnector(usb, settings)

        every { usb.isDevicePresent } returns true
        assertTrue(connector.isDevicePresent)
        every { usb.isDevicePresent } returns false
        assertFalse(connector.isDevicePresent)
    }

    @Test
    fun model_speculosOn_reportsUnknownModel() {
        every { settings.enabled } returns true

        assertEquals(LedgerModel.Unknown, SpeculosSwitchingLedgerConnector(usb, settings).model)
    }
}
