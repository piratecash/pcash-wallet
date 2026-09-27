package cash.p.terminal.modules.qrscanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import cash.p.terminal.core.openInputStreamSafe
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.withContext
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow

class QrCodeImageDecoder(
    private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
) {

    suspend fun decode(uri: Uri): Result<String> = withContext(dispatcherProvider.default) {
        runCatching {
            decodeFirstMatch(uri)
        }.mapCatching { result ->
            result.text?.takeIf { it.isNotBlank() } ?: error("QR code has no textual content")
        }
    }

    /**
     * A dense QR photographed by a camera survives only a mild downscale, so a miss at the
     * smallest target is not proof the image has no code: retry at a higher resolution.
     */
    private fun decodeFirstMatch(uri: Uri): com.google.zxing.Result {
        val bounds = decodeBounds(uri)
        val sampleSizes = DECODE_DIMENSIONS
            .map { calculateInSampleSize(bounds.outWidth, bounds.outHeight, it) }
            .distinct()

        var failure: ReaderException? = null
        for (sampleSize in sampleSizes) {
            val bitmap = decodeBitmap(uri, sampleSize)
            try {
                return decodeFromBitmap(bitmap)
            } catch (e: ReaderException) {
                failure = e
            } finally {
                bitmap.recycle()
            }
        }
        throw failure ?: NotFoundException.getNotFoundInstance()
    }

    private fun decodeFromBitmap(bitmap: Bitmap): com.google.zxing.Result {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val source = RGBLuminanceSource(width, height, pixels)
        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
        val reader = MultiFormatReader().apply {
            setHints(
                mapOf(
                    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                    DecodeHintType.TRY_HARDER to true
                )
            )
        }

        return reader.decodeWithState(binaryBitmap).also {
            reader.reset()
        }
    }

    private fun decodeBounds(uri: Uri): BitmapFactory.Options {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStreamSafe(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
        return options
    }

    private fun decodeBitmap(uri: Uri, sampleSize: Int): Bitmap {
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        return context.contentResolver.openInputStreamSafe(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: error("Unable to decode image")
    }

    private fun calculateInSampleSize(
        imageWidth: Int,
        imageHeight: Int,
        requiredDimension: Int
    ): Int {
        if (imageWidth <= 0 ||
            imageHeight <= 0 ||
            (imageWidth <= requiredDimension && imageHeight <= requiredDimension)
        ) {
            return 1
        }

        // Calculate the ratio of the larger dimension to the required dimension.
        val largestDimension = max(imageWidth, imageHeight)
        val ratio = largestDimension.toFloat() / requiredDimension.toFloat()

        // Find the nearest power of 2 for the sample size.
        // Using ln(ratio) / ln(2) finds the exponent 'x' in 2^x = ratio.
        // Flooring this gives us the largest power of 2 less than or equal to the ratio.
        val powerOf2 = floor(ln(ratio) / ln(2.0))

        return 2.0.pow(powerOf2).toInt()
    }

    companion object {
        private val DECODE_DIMENSIONS = listOf(1024, 2048)
    }
}
