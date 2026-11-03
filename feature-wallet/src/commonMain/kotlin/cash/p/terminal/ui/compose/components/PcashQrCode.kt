package cash.p.terminal.ui.compose.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import cash.p.terminal.core.tryOrNull
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import io.github.alexzhirkevich.qrose.options.QrBallShape
import io.github.alexzhirkevich.qrose.options.QrErrorCorrectionLevel
import io.github.alexzhirkevich.qrose.options.QrFrameShape
import io.github.alexzhirkevich.qrose.options.QrLogoPadding
import io.github.alexzhirkevich.qrose.options.QrLogoShape
import io.github.alexzhirkevich.qrose.options.QrPixelShape
import io.github.alexzhirkevich.qrose.options.roundCorners
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import kotlin.math.roundToInt

object PcashQrCodeDefaults {
    val Size = 300.dp
    val QuietZone = 8.dp
    const val QuietZoneModules = 4f
    const val SavedBitmapSize = 1024

    // Numeric mode at ECC Q tops out at 3993 chars; no denser mode exists, so nothing
    // longer can be encoded at this ECC level in any mode.
    const val MaxEncodableChars = 3_993

    // Encodable is not the same as scannable: past this module count the rounded pixel shapes
    // and the centre logo stop decoding reliably even from the rendered bitmap at full
    // resolution, long before the camera is involved. Measured against this exact rendering and
    // the app's scan pipeline, decoding is flawless up to 129 modules and collapses at 133-137.
    // 113 (QR version 24, 661 chars in byte mode) keeps a margin for the blur, glare and tilt a
    // real camera adds. Only callers with a fallback may apply this limit — rejecting a dense
    // payload with nothing to fall back to would show an empty panel instead of a hard-to-scan
    // but still valid code.
    const val MaxReadableModules = 113

    // QRose MediumHigh maps to QR ECC Q; keep this paired with ZXing Q for capacity checks.
    val PainterErrorCorrectionLevel = QrErrorCorrectionLevel.MediumHigh
    val EncoderErrorCorrectionLevel = ErrorCorrectionLevel.Q
}

@Composable
internal expect fun rememberPcashQrLogoPainter(): Painter

/** Null when the content is too dense to scan — the caller must fall back to the animated tape. */
@Composable
fun rememberReadablePcashQrCodePainterOrNull(content: String): Painter? {
    val readable = remember(content) { isReadablePcashQrCode(content) }
    if (!readable) return null
    return rememberPcashQrCodePainter(content)
}

@Composable
fun rememberPcashQrCodePainter(
    content: String,
    withLogo: Boolean = true,
): Painter {
    val logoPainter = rememberPcashQrLogoPainter()

    return rememberQrCodePainter(content) {
        errorCorrectionLevel = PcashQrCodeDefaults.PainterErrorCorrectionLevel
        if (withLogo) {
            logo {
                painter = logoPainter
                padding = QrLogoPadding.Natural(.25f)
                shape = QrLogoShape.roundCorners(0.8f)
                size = 0.2f
            }
        }

        shapes {
            ball = QrBallShape.roundCorners(.25f)
            darkPixel = QrPixelShape.roundCorners()
            frame = QrFrameShape.roundCorners(.25f)
        }
    }
}

@Composable
fun PcashQrCodeImage(
    content: String,
    qrCodePainter: Painter,
    modifier: Modifier = Modifier,
    qrCodeSize: Dp = PcashQrCodeDefaults.Size,
    contentScale: ContentScale = ContentScale.FillWidth,
) {
    val quietZone = remember(content, qrCodeSize) {
        calculatePcashQrQuietZone(content, qrCodeSize)
    }
    Image(
        painter = qrCodePainter,
        modifier = modifier
            .padding(quietZone)
            .fillMaxSize(),
        contentScale = contentScale,
        contentDescription = null,
    )
}

@Composable
fun PcashQrCodeImage(
    content: String,
    modifier: Modifier = Modifier,
    qrCodeSize: Dp = PcashQrCodeDefaults.Size,
    contentScale: ContentScale = ContentScale.FillWidth,
) {
    val encodable = remember(content) { canEncodeAsPcashQrCode(content) }
    if (!encodable) return
    PcashQrCodeImage(
        content = content,
        qrCodePainter = rememberPcashQrCodePainter(content),
        modifier = modifier,
        qrCodeSize = qrCodeSize,
        contentScale = contentScale,
    )
}

private fun calculatePcashQrQuietZone(content: String, qrCodeSize: Dp): Dp {
    val modules = qrModuleCount(content) ?: return PcashQrCodeDefaults.QuietZone
    val moduleSize = qrCodeSize.value / modules
    return (moduleSize * PcashQrCodeDefaults.QuietZoneModules).dp
        .coerceAtLeast(PcashQrCodeDefaults.QuietZone)
}

fun canEncodeAsPcashQrCode(content: String): Boolean = qrModuleCount(content) != null

fun isReadablePcashQrCode(content: String): Boolean {
    val modules = qrModuleCount(content) ?: return false
    return modules <= PcashQrCodeDefaults.MaxReadableModules
}

internal fun qrModuleCount(content: String): Int? {
    if (content.length > PcashQrCodeDefaults.MaxEncodableChars) return null
    return tryOrNull {
        Encoder.encode(content, PcashQrCodeDefaults.EncoderErrorCorrectionLevel).matrix?.width?.takeIf { it > 0 }
    }
}

fun createPcashQrCodeBitmap(
    content: String,
    painter: Painter,
    density: Density,
): ImageBitmap {
    val quietZone = calculatePcashQrQuietZonePx(content, density)
    val bitmapSize = PcashQrCodeDefaults.SavedBitmapSize + quietZone * 2
    val bitmap = ImageBitmap(bitmapSize, bitmapSize)
    val canvas = Canvas(bitmap)
    val drawScope = CanvasDrawScope()

    drawScope.draw(
        density = density,
        layoutDirection = LayoutDirection.Ltr,
        canvas = canvas,
        size = Size(bitmapSize.toFloat(), bitmapSize.toFloat()),
    ) {
        drawRect(Color.White)
        translate(left = quietZone.toFloat(), top = quietZone.toFloat()) {
            with(painter) {
                draw(
                    size = Size(
                        PcashQrCodeDefaults.SavedBitmapSize.toFloat(),
                        PcashQrCodeDefaults.SavedBitmapSize.toFloat()
                    )
                )
            }
        }
    }

    return bitmap
}

private fun calculatePcashQrQuietZonePx(content: String, density: Density): Int {
    val defaultQuietZone = with(density) { PcashQrCodeDefaults.QuietZone.toPx() }
    val modules = qrModuleCount(content) ?: return defaultQuietZone.roundToInt()
    val moduleSize = PcashQrCodeDefaults.SavedBitmapSize.toFloat() / modules
    return (moduleSize * PcashQrCodeDefaults.QuietZoneModules)
        .coerceAtLeast(defaultQuietZone)
        .roundToInt()
}
