package cash.p.terminal.modules.qrscanner

import com.google.zxing.BarcodeFormat
import com.google.zxing.LuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QrCodeReaderTest {

    private val reader = QrCodeReader()

    @Test
    fun decode_plainQr_returnsText() {
        assertEquals(TEXT, reader.decode(qrSource(TEXT)))
    }

    @Test
    fun decode_invertedQr_returnsText() {
        assertEquals(TEXT, reader.decode(qrSource(TEXT, inverted = true)))
    }

    @Test
    fun decode_noQr_returnsNull() {
        assertNull(reader.decode(RGBLuminanceSource(SIZE, SIZE, IntArray(SIZE * SIZE) { WHITE })))
    }

    @Test
    fun decode_qrInsideCentreCrop_cropToCenter_returnsText() {
        assertEquals(TEXT, reader.decode(qrSource(TEXT, padding = 80), cropToCenter = true))
    }

    @Test
    fun decode_sameReaderTwice_stillDecodes() {
        assertEquals(TEXT, reader.decode(qrSource(TEXT)))
        assertEquals(TEXT, reader.decode(qrSource(TEXT)))
    }

    private fun qrSource(text: String, inverted: Boolean = false, padding: Int = 0): LuminanceSource {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, SIZE, SIZE)
        val side = SIZE + 2 * padding
        val background = if (inverted) BLACK else WHITE
        val ink = if (inverted) WHITE else BLACK
        val pixels = IntArray(side * side) { background }
        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                if (matrix[x, y]) pixels[(y + padding) * side + x + padding] = ink
            }
        }
        return RGBLuminanceSource(side, side, pixels)
    }

    private companion object {
        const val TEXT = "bitcoin:1abcXYZ"
        const val SIZE = 300
        const val WHITE = 0xFFFFFFFF.toInt()
        const val BLACK = 0xFF000000.toInt()
    }
}
