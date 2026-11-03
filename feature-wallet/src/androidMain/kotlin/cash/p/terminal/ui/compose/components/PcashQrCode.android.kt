package cash.p.terminal.ui.compose.components

import android.annotation.SuppressLint
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.launcher_main_preview
import org.jetbrains.compose.resources.painterResource

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

private const val LAUNCHER_ICON = "launcher_main"
