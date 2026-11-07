package cash.p.terminal.modules.qrscanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.net.toUri
import cash.p.terminal.core.openInputStreamSafe
import cash.p.terminal.core.tryOrNull
import com.google.zxing.RGBLuminanceSource
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.withContext
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow

class QrCodeImageDecoderImpl(
    private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
) : QrCodeImageDecoder {

    override suspend fun decode(imageRef: String): String? = withContext(dispatcherProvider.default) {
        tryOrNull { decodeFirstMatch(imageRef.toUri()) }
    }

    /**
     * A dense QR photographed by a camera survives only a mild downscale, so a miss at the
     * smallest target is not proof the image has no code: retry at a higher resolution.
     */
    private fun decodeFirstMatch(uri: Uri): String? {
        val bounds = decodeBounds(uri)
        val sampleSizes = DECODE_DIMENSIONS
            .map { calculateInSampleSize(bounds.outWidth, bounds.outHeight, it) }
            .distinct()

        val reader = QrCodeReader()
        for (sampleSize in sampleSizes) {
            val bitmap = decodeBitmap(uri, sampleSize)
            try {
                reader.decode(bitmap.toLuminanceSource())?.let { return it }
            } finally {
                bitmap.recycle()
            }
        }
        return null
    }

    private fun Bitmap.toLuminanceSource(): RGBLuminanceSource {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        return RGBLuminanceSource(width, height, pixels)
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
        if (imageWidth <= 0 || imageHeight <= 0) return 1
        val largestDimension = max(imageWidth, imageHeight)
        if (largestDimension <= requiredDimension) return 1

        // Calculate the ratio of the larger dimension to the required dimension.
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
