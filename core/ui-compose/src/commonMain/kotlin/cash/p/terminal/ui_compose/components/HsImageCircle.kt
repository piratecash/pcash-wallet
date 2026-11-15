package cash.p.terminal.ui_compose.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.coin_placeholder
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.jetbrains.compose.resources.DrawableResource

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
    colorFilter: ColorFilter? = null,
    contentScale: ContentScale = ContentScale.Fit,
    showAlternativeWhileLoading: Boolean = false,
) {
    val fallback = placeholder ?: Res.drawable.coin_placeholder
    val alternative = alternativeUrl?.takeIf { url != null }?.let {
        rememberAsyncImagePainter(model = it, error = placeholderPainter(fallback))
    }
    // Crossfade makes both images half-transparent midway, which reads as a blink over the alternative.
    val context = LocalPlatformContext.current
    val model = remember(url, showAlternativeWhileLoading) {
        if (showAlternativeWhileLoading) ImageRequest.Builder(context).data(url).crossfade(false).build() else url
    }
    when {
        url != null -> Image(
            painter = rememberAsyncImagePainter(
                model = model,
                placeholder = alternative.takeIf { showAlternativeWhileLoading },
                error = alternative ?: placeholderPainter(fallback)
            ),
            contentDescription = null,
            modifier = modifier,
            colorFilter = colorFilter,
            // Default Fit (not FillBounds) so non-square source logos keep their aspect ratio and are
            // centered within the square container instead of being stretched.
            contentScale = contentScale
        )

        else -> Image(
            painter = placeholderPainter(fallback),
            contentDescription = null,
            modifier = modifier,
            colorFilter = colorFilter,
            contentScale = contentScale
        )
    }
}
