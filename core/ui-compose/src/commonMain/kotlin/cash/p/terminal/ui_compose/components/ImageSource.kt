package cash.p.terminal.ui_compose.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_placeholder
import coil3.compose.rememberAsyncImagePainter
import org.jetbrains.compose.resources.DrawableResource

sealed class ImageSource {
    class Local(val res: DrawableResource) : ImageSource()
    class Remote(
        val url: String,
        val placeholder: DrawableResource = Res.drawable.ic_placeholder,
        val alternativeUrl: String? = null
    ) : ImageSource()

    @Composable
    fun painter(): Painter = when (this) {
        is Local -> placeholderPainter(res)
        is Remote -> rememberAsyncImagePainter(
            model = url,
            error = alternativeUrl?.let {
                rememberAsyncImagePainter(
                    model = alternativeUrl,
                    error = placeholderPainter(placeholder)
                )
            } ?: placeholderPainter(placeholder)
        )
    }
}
