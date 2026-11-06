package cash.p.terminal.shared.desktop.qrscanner

import cash.p.terminal.core.tryOrNull
import cash.p.terminal.modules.qrscanner.QrCodeImageDecoder
import cash.p.terminal.modules.qrscanner.QrCodeReader
import com.google.zxing.LuminanceSource
import com.google.zxing.RGBLuminanceSource
import io.horizontalsystems.core.DispatcherProvider
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.withContext

/** Decodes a QR code from an image file; [decode] takes the file path. */
class DesktopQrCodeImageDecoder(private val dispatcherProvider: DispatcherProvider) : QrCodeImageDecoder {

    override suspend fun decode(imageRef: String): String? = withContext(dispatcherProvider.default) {
        tryOrNull { ImageIO.read(File(imageRef))?.let(::decodeImage) }
    }

    // A small dense code in a large photo may not survive the downscale: retry at full size.
    private fun decodeImage(image: BufferedImage): String? {
        val reader = QrCodeReader()
        val scaled = image.scaledDown()
        return reader.decode(scaled.toLuminanceSource())
            ?: if (scaled !== image) reader.decode(image.toLuminanceSource()) else null
    }

    private fun BufferedImage.scaledDown(): BufferedImage {
        val scale = MAX_SIDE.toDouble() / max(width, height)
        if (scale >= 1) return this
        val scaledWidth = (width * scale).roundToInt()
        val scaled = BufferedImage(scaledWidth, (height * scale).roundToInt(), BufferedImage.TYPE_INT_RGB)
        val graphics = scaled.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            graphics.drawImage(this, 0, 0, scaled.width, scaled.height, null)
        } finally {
            graphics.dispose()
        }
        return scaled
    }

    private companion object {
        const val MAX_SIDE = 2048
    }
}

internal fun BufferedImage.toLuminanceSource(): LuminanceSource =
    RGBLuminanceSource(width, height, getRGB(0, 0, width, height, null, 0, width))
