package cash.p.terminal.shared.desktop.qrscanner

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopWebcamTest {

    @Test
    fun orderDevices_rememberedPresent_putsItFirstKeepingTheRestInOrder() {
        val order = orderDevices(listOf(builtIn, usb, phone), rememberedName = "USB")

        assertEquals(listOf(usb, builtIn, phone), order)
    }

    @Test
    fun orderDevices_rememberedAbsentOrNull_keepsEnumerationOrder() {
        assertEquals(listOf(builtIn, usb), orderDevices(listOf(builtIn, usb), rememberedName = "Gone"))
        assertEquals(listOf(builtIn, usb), orderDevices(listOf(builtIn, usb), rememberedName = null))
    }

    @Test
    fun orderDevices_deviceWithoutFormats_isDropped() {
        val noFormats = WebcamDevice(id = "3", name = "Virtual", formats = emptyList())

        assertEquals(listOf(usb), orderDevices(listOf(noFormats, usb), rememberedName = "Virtual"))
    }

    @Test
    fun orderDevices_selectedIdAmongSameNames_putsSelectedFirst() {
        val twinA = WebcamDevice(id = "a", name = "USB Camera", formats = listOf(hd))
        val twinB = WebcamDevice(id = "b", name = "USB Camera", formats = listOf(hd))
        val cases = listOf(
            Triple("b", "USB Camera", listOf(twinB, twinA, builtIn)),
            Triple("a", "USB Camera", listOf(twinA, twinB, builtIn)),
            Triple(null, "USB Camera", listOf(twinA, twinB, builtIn)),
            Triple("b", null, listOf(twinB, builtIn, twinA)),
        )

        cases.forEach { (selectedId, rememberedName, expected) ->
            assertEquals(expected, orderDevices(listOf(builtIn, twinA, twinB), rememberedName, selectedId), "$selectedId")
        }
    }

    @Test
    fun chooseFormat_table_picksExpectedFormat() {
        val cases = listOf(
            Triple(listOf(vga, hd, fullHd), DesktopOs.MacOs, hd),
            Triple(listOf(vga, fullHd, uhd), DesktopOs.Windows, vga),
            Triple(listOf(vga, tallHd), DesktopOs.Windows, vga),
            Triple(listOf(uhd, uhdSlow), DesktopOs.MacOs, uhd),
            Triple(listOf(hd.copy(fps = 15), hd), DesktopOs.Windows, hd),
            Triple(listOf(mjpgHd, yuyvVga), DesktopOs.Linux, yuyvVga),
            Triple(listOf(mjpgHd, yuyvVga, rgbVga), DesktopOs.Linux, rgbVga),
            Triple(listOf(unknownHd, mjpgHd), DesktopOs.Linux, mjpgHd),
            Triple(listOf(unknownHd), DesktopOs.Linux, unknownHd),
            Triple(listOf(mjpgHd, yuyvVga), DesktopOs.MacOs, mjpgHd),
        )

        cases.forEach { (formats, os, expected) ->
            assertEquals(expected, chooseFormat(formats, os), "$formats on $os")
        }
    }

    @Test
    fun chooseFormat_noFormats_returnsNull() {
        assertNull(chooseFormat(emptyList(), DesktopOs.Linux))
    }

    @Test
    fun nextAfterNoFrames_table_returnsFollowingDeviceOrNull() {
        val order = listOf(builtIn, usb, phone)
        val cases = listOf(builtIn to usb, usb to phone, phone to null)

        cases.forEach { (current, expected) -> assertEquals(expected, nextAfterNoFrames(order, current), current.name) }
        assertNull(nextAfterNoFrames(listOf(usb), builtIn))
    }

    private companion object {
        val yuyv = fourcc("YUYV")
        val mjpg = fourcc("MJPG")
        val vga = WebcamFormat(640, 480, mjpg, 30)
        val hd = WebcamFormat(1280, 720, mjpg, 30)
        val fullHd = WebcamFormat(1920, 1080, mjpg, 30)
        val uhd = WebcamFormat(3840, 2160, mjpg, 30)
        val tallHd = WebcamFormat(1280, 1088, mjpg, 30)
        val uhdSlow = WebcamFormat(4096, 2160, mjpg, 15)
        val mjpgHd = WebcamFormat(1280, 720, mjpg, 30)
        val yuyvVga = WebcamFormat(640, 480, yuyv, 30)
        val rgbVga = WebcamFormat(640, 480, fourcc("RGB3"), 30)
        val unknownHd = WebcamFormat(1280, 720, fourcc("H264"), 30)
        val builtIn = WebcamDevice(id = "0", name = "Built-in", formats = listOf(hd))
        val usb = WebcamDevice(id = "1", name = "USB", formats = listOf(vga))
        val phone = WebcamDevice(id = "2", name = "Phone", formats = listOf(fullHd))
    }
}
