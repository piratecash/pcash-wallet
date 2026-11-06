package cash.p.terminal.shared.desktop.qrscanner

import cash.p.terminal.core.TestDispatcherProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

class DesktopQrCodeImageDecoderTest {

    private val dispatcher = StandardTestDispatcher()
    private val decoder = DesktopQrCodeImageDecoder(TestDispatcherProvider(dispatcher, TestScope(dispatcher)))
    private val directory = createTempDirectory().toFile()

    @AfterTest
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun decode_pngWithQr_returnsText() = runTest(dispatcher) {
        assertEquals(TEXT, decoder.decode(qrPng(width = 400, height = 400, qrSize = 400).path))
    }

    @Test
    fun decode_nonImageFile_returnsNull() = runTest(dispatcher) {
        val file = File(directory, "notes.png").apply { writeText("not an image") }

        assertNull(decoder.decode(file.path))
    }

    @Test
    fun decode_missingFile_returnsNull() = runTest(dispatcher) {
        assertNull(decoder.decode(File(directory, "missing.png").path))
    }

    @Test
    fun decode_hugeImage_downscalesAndReturnsText() = runTest(dispatcher) {
        assertEquals(TEXT, decoder.decode(qrPng(width = 5000, height = 5000, qrSize = 5000).path))
    }

    @Test
    fun decode_smallQrInHugeImage_decodesAtFullSize() = runTest(dispatcher) {
        // 3 px modules shrink below one pixel in the 2048 px pass; only the full-size retry can read them.
        assertEquals(TEXT, decoder.decode(qrPng(width = 8000, height = 2000, qrSize = 87).path))
    }

    /** A [qrSize] px QR code centred on a white [width]×[height] image. */
    private fun qrPng(width: Int, height: Int, qrSize: Int): File {
        val matrix = QRCodeWriter().encode(TEXT, BarcodeFormat.QR_CODE, qrSize, qrSize)
        val image = BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY)
        image.createGraphics().apply {
            color = Color.WHITE
            fillRect(0, 0, width, height)
            dispose()
        }
        val left = (width - matrix.width) / 2
        val top = (height - matrix.height) / 2
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix[x, y]) image.raster.setSample(left + x, top + y, 0, BLACK)
            }
        }
        return File(directory, "qr-${width}x$height-$qrSize.png").also { ImageIO.write(image, "png", it) }
    }

    private companion object {
        const val TEXT = "bitcoin:1abcXYZ"
        const val BLACK = 0
    }
}
