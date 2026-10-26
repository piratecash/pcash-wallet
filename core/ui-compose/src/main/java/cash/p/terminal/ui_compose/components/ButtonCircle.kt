package cash.p.terminal.ui_compose.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import cash.p.terminal.ui_compose.R
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Composable
fun ButtonPrimaryCircle(
    @DrawableRes icon: Int = R.drawable.ic_arrow_down_left_24,
    contentDescription: String? = null,
    onClick: () -> Unit,
    enabled: Boolean = true,
    iconTint: Color? = null,
    background: Color? = null,
) {
    ButtonPrimaryCircle(
        painter = painterResource(id = icon),
        onClick = onClick,
        contentDescription = contentDescription,
        enabled = enabled,
        iconTint = iconTint,
        background = background,
    )
}

@Composable
fun ButtonSecondaryCircle(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes icon: Int = R.drawable.ic_arrow_down_20,
    contentDescription: String? = null,
    tint: Color = ComposeAppTheme.colors.iconPrimary,
    background: Color = ComposeAppTheme.colors.buttonSecondaryFilledBackground,
    border: BorderStroke? = SecondaryButtonDefaults.filledBorder(),
    onClick: () -> Unit,
) {
    ButtonSecondaryCircle(
        painter = painterResource(id = icon),
        onClick = onClick,
        modifier = modifier.secondaryCircle(background, border),
        enabled = enabled,
        contentDescription = contentDescription,
        tint = tint,
    )
}
