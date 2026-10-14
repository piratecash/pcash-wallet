package cash.p.terminal.ui_compose.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.coin_placeholder
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

private class TintedPainter(
    private val base: Painter,
    private val tint: Color,
) : Painter() {
    override val intrinsicSize: Size = base.intrinsicSize
    override fun DrawScope.onDraw() {
        with(base) { draw(size, colorFilter = ColorFilter.tint(tint)) }
    }
}

fun Painter.tinted(color: Color): Painter = TintedPainter(this, color)

/** The coin placeholder vector is opaque (no night variants in CMP); its alpha comes from the theme. */
@Composable
fun placeholderPainter(res: DrawableResource): Painter {
    val painter = painterResource(res)
    return if (res == Res.drawable.coin_placeholder) painter.tinted(ComposeAppTheme.colors.coinPlaceholder) else painter
}
