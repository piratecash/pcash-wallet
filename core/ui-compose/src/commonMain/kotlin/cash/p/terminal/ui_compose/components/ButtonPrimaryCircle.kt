package cash.p.terminal.ui_compose.components

import androidx.compose.foundation.background
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
fun ButtonPrimaryCircle(
    icon: DrawableResource,
    onClick: () -> Unit,
    contentDescription: String? = null,
    enabled: Boolean = true,
    iconTint: Color? = null,
    background: Color? = null,
) {
    ButtonPrimaryCircle(
        painter = painterResource(icon),
        onClick = onClick,
        contentDescription = contentDescription,
        enabled = enabled,
        iconTint = iconTint,
        background = background,
    )
}

@Composable
internal fun ButtonPrimaryCircle(
    painter: Painter,
    onClick: () -> Unit,
    contentDescription: String?,
    enabled: Boolean,
    iconTint: Color?,
    background: Color?,
) {
    val shape = CircleShape
    val resolvedBackground = if (enabled) {
        background ?: ComposeAppTheme.colors.textPrimary
    } else {
        ComposeAppTheme.colors.buttonPrimaryDisabledBackground
    }
    val resolvedTint = if (enabled) {
        iconTint ?: ComposeAppTheme.colors.contentInverse
    } else {
        ComposeAppTheme.colors.iconDisabled
    }

    HsIconButton(
        onClick = { onClick() },
        modifier = Modifier
            .size(50.dp)
            .clip(shape)
            .background(resolvedBackground),
        enabled = enabled,
        rippleColor = ComposeAppTheme.colors.contentInverse
    ) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = resolvedTint
        )
    }
}
