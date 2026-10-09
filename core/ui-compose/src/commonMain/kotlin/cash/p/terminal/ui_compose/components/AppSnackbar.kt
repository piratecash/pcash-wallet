package cash.p.terminal.ui_compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

enum class AppSnackbarVariant { Success, Error, Warning, InProgress, Message, Premium }

private val SnackbarShape = RoundedCornerShape(4.dp)

/** The icon comes from the caller; the variant only decides its tint. */
@Composable
fun AppSnackbar(
    text: String,
    variant: AppSnackbarVariant,
    icon: Painter?,
    modifier: Modifier = Modifier,
) {
    val colors = ComposeAppTheme.colors
    val contentColor = variant.contentColor()
    Row(
        modifier = modifier
            .shadow(elevation = 4.dp, shape = SnackbarShape)
            .fillMaxWidth()
            .background(variant.fillColor(), SnackbarShape)
            .heightIn(min = 67.dp)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = ComposeAppTheme.typography.subhead1,
            color = contentColor,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        if (variant == AppSnackbarVariant.InProgress) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = contentColor,
                trackColor = colors.transparent,
                strokeWidth = 2.dp,
            )
        } else if (icon != null) {
            val tint = if (variant == AppSnackbarVariant.Warning) colors.statusWarning else contentColor
            Icon(painter = icon, contentDescription = null, tint = tint)
        }
    }
}

@Composable
private fun AppSnackbarVariant.fillColor(): Color = with(ComposeAppTheme.colors) {
    when (this@fillColor) {
        AppSnackbarVariant.Success -> statusSuccess
        AppSnackbarVariant.Error -> statusError
        AppSnackbarVariant.Premium -> statusWarning
        AppSnackbarVariant.Warning,
        AppSnackbarVariant.InProgress,
        AppSnackbarVariant.Message -> snackbarNeutralBackground
    }
}

@Composable
private fun AppSnackbarVariant.contentColor(): Color = with(ComposeAppTheme.colors) {
    if (this@contentColor == AppSnackbarVariant.Success) textPrimary else contentOnColor
}
