package cash.p.terminal.ui_compose.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.coin_placeholder
import org.jetbrains.compose.resources.DrawableResource
import coil3.compose.rememberAsyncImagePainter

@Composable
fun HsImageCircle(
    modifier: Modifier,
    url: String?,
    alternativeUrl: String? = null,
    placeholder: DrawableResource? = null,
    colorFilter: ColorFilter? = null
) {
    HsImage(
        url = url,
        alternativeUrl = alternativeUrl,
        placeholder = placeholder,
        modifier = modifier.clip(CircleShape),
        colorFilter = colorFilter
    )
}


@Composable
fun HsImage(
    url: String?,
    alternativeUrl: String? = null,
    placeholder: DrawableResource? = null,
    modifier: Modifier,
    colorFilter: ColorFilter? = null
) {
    val fallback = placeholder ?: Res.drawable.coin_placeholder
    when {
        url != null -> Image(
            painter = rememberAsyncImagePainter(
                model = url,
                error = alternativeUrl?.let {
                    rememberAsyncImagePainter(
                        model = alternativeUrl,
                        error = placeholderPainter(fallback)
                    )
                } ?: placeholderPainter(fallback)
            ),
            contentDescription = null,
            modifier = modifier,
            colorFilter = colorFilter,
            // Fit (not FillBounds) so non-square source logos keep their aspect ratio and are
            // centered within the square container instead of being stretched.
            contentScale = ContentScale.Fit
        )

        else -> Image(
            painter = placeholderPainter(fallback),
            contentDescription = null,
            modifier = modifier,
            colorFilter = colorFilter
        )
    }
}
