package cash.p.terminal.modules.xtransaction.helpers

import androidx.compose.runtime.Composable
import cash.p.terminal.ui_compose.components.placeholderPainter
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.coin_placeholder
import coil3.compose.rememberAsyncImagePainter
import org.jetbrains.compose.resources.DrawableResource

@Composable
fun coinIconPainter(
    url: String?,
    alternativeUrl: String?,
    placeholder: DrawableResource?,
    fallback: DrawableResource = placeholder ?: Res.drawable.coin_placeholder
) = rememberAsyncImagePainter(
    model = url,
    error = alternativeUrl?.let {
        rememberAsyncImagePainter(
            model = it,
            error = placeholderPainter(fallback)
        )
    } ?: placeholderPainter(fallback)
)
