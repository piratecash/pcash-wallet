package cash.p.terminal.modules.qrscanner

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.common.HybridBinarizer

/** Not thread-safe: every concurrent caller owns its own instance. */
class QrCodeReader {

    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true,
                DecodeHintType.ALSO_INVERTED to true
            )
        )
    }

    /** [cropToCenter] zooms camera frames in, so a code sitting inside the scan window looks larger. */
    fun decode(source: LuminanceSource, cropToCenter: Boolean = false): String? {
        val input = if (cropToCenter && source.isCropSupported) source.centerCrop() else source
        return try {
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(input))).text?.takeIf { it.isNotBlank() }
        } catch (_: NotFoundException) {
            null
        } finally {
            reader.reset()
        }
    }

    private fun LuminanceSource.centerCrop(): LuminanceSource {
        val cropWidth = (width * CROP_FACTOR).toInt()
        val cropHeight = (height * CROP_FACTOR).toInt()
        return crop((width - cropWidth) / 2, (height - cropHeight) / 2, cropWidth, cropHeight)
    }

    private companion object {
        const val CROP_FACTOR = 0.7
    }
}
