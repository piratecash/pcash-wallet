package cash.p.terminal.ui_compose.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Composable
fun ButtonSecondaryCircle(
    icon: DrawableResource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
    tint: Color = ComposeAppTheme.colors.iconPrimary,
    background: Color = ComposeAppTheme.colors.buttonSecondaryFilledBackground,
    border: BorderStroke? = SecondaryButtonDefaults.filledBorder(),
) {
    ButtonSecondaryCircle(
        painter = painterResource(icon),
        onClick = onClick,
        modifier = modifier.secondaryCircle(background, border),
        enabled = enabled,
        contentDescription = contentDescription,
        tint = tint,
    )
}

internal fun Modifier.secondaryCircle(background: Color, border: BorderStroke?) = this
    .size(28.dp)
    .clip(CircleShape)
    .background(background)
    .then(if (border != null) Modifier.border(border, CircleShape) else Modifier)

@Composable
internal fun ButtonSecondaryCircle(
    painter: Painter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
    tint: Color = ComposeAppTheme.colors.iconPrimary,
) {
    HsIconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        rippleColor = tint
    ) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = if (enabled) tint else ComposeAppTheme.colors.iconDisabled
        )
    }
}
