package cash.p.terminal.ui.compose.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.launcher_main_preview
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

fun createPcashQrCodeBitmap(
    content: String,
    painter: Painter,
    density: Density,
): Bitmap {
    val quietZone = calculatePcashQrQuietZonePx(content, density)
    val bitmapSize = PcashQrCodeDefaults.SavedBitmapSize + quietZone * 2
    val bitmap = Bitmap.createBitmap(bitmapSize, bitmapSize, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap.asImageBitmap())
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

// launcher_main is an app resource that build types override, so it is resolved by name.
@SuppressLint("DiscouragedApi")
@Composable
internal actual fun rememberPcashQrLogoPainter(): Painter {
    val context = LocalContext.current
    return remember {
        context.resources.getIdentifier(LAUNCHER_ICON, "mipmap", context.packageName)
            .takeIf { it != 0 }
            ?.let { ResourcesCompat.getDrawable(context.resources, it, context.theme) as? AdaptiveIconDrawable }
            ?.let { BitmapPainter(it.toBitmap().asImageBitmap()) }
    } ?: painterResource(Res.drawable.launcher_main_preview)
}

private fun calculatePcashQrQuietZonePx(content: String, density: Density): Int {
    val defaultQuietZone = with(density) { PcashQrCodeDefaults.QuietZone.toPx() }
    val modules = qrModuleCount(content) ?: return defaultQuietZone.roundToInt()
    val moduleSize = PcashQrCodeDefaults.SavedBitmapSize.toFloat() / modules
    return (moduleSize * PcashQrCodeDefaults.QuietZoneModules)
        .coerceAtLeast(defaultQuietZone)
        .roundToInt()
}

private const val LAUNCHER_ICON = "launcher_main"
