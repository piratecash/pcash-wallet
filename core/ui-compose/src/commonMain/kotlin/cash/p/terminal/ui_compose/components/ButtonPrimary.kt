package cash.p.terminal.ui_compose.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Composable
fun ButtonPrimaryDefaultWithIcon(
    modifier: Modifier = Modifier,
    icon: Painter,
    iconTint: Color? = null,
    title: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    ButtonPrimary(
        modifier = modifier,
        onClick = onClick,
        buttonColors = ButtonPrimaryDefaults.filledColors(
            background = ComposeAppTheme.colors.buttonPrimaryNeutralBackground,
            content = ComposeAppTheme.colors.buttonPrimaryNeutralContent,
        ),
        content = {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = if (enabled) iconTint ?: LocalContentColor.current else ComposeAppTheme.colors.iconDisabled
            )
            HSpacer(width = 8.dp)
            Text(
                title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        enabled = enabled
    )
}

@Composable
fun ButtonPrimaryDefault(
    modifier: Modifier = Modifier,
    title: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    ButtonPrimary(
        modifier = modifier,
        onClick = onClick,
        buttonColors = ButtonPrimaryDefaults.filledColors(
            background = ComposeAppTheme.colors.buttonPrimaryNeutralBackground,
            content = ComposeAppTheme.colors.buttonPrimaryNeutralContent,
        ),
        content = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        enabled = enabled
    )
}

@Composable
fun ButtonPrimaryTransparent(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    textColor: Color= Color.Unspecified,
    enabled: Boolean = true
) {

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val contentColor = when {
        !enabled -> ComposeAppTheme.colors.textDisabled
        isPressed -> ComposeAppTheme.colors.textSecondary
        else -> ComposeAppTheme.colors.buttonPrimaryOutlineContent
    }

    Surface(
        modifier = modifier,
        shape = ButtonPrimaryDefaults.Shape,
        color = ComposeAppTheme.colors.transparent,
        contentColor = contentColor,
        border = BorderStroke(1.dp, ComposeAppTheme.colors.buttonPrimaryOutlineBorder),
    ) {
        ProvideTextStyle(
            value = ComposeAppTheme.typography.headline2
        ) {
            Row(
                Modifier
                    .defaultMinSize(
                        minWidth = ButtonPrimaryDefaults.MinWidth,
                        minHeight = ButtonPrimaryDefaults.MinHeight
                    )
                    .padding(ButtonPrimaryDefaults.ContentPadding)
                    .clickable(
                        enabled = enabled,
                        onClick = onClick,
                        interactionSource = interactionSource,
                        indication = null
                    ),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                content = {
                    Text(
                        text = title,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}

@Composable
fun ButtonPrimaryYellow(
    modifier: Modifier = Modifier,
    title: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    loadingIndicator: Boolean = false
) {
    ButtonPrimary(
        modifier = modifier,
        onClick = onClick,
        buttonColors = ButtonPrimaryDefaults.filledColors(
            background = ComposeAppTheme.colors.brandDefault,
            content = ComposeAppTheme.colors.buttonPrimaryBrandContent,
        ),
        content = {
            if (loadingIndicator) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = ComposeAppTheme.colors.textSecondary,
                    strokeWidth = 2.dp
                )
                HSpacer(width = 8.dp)
            }

            Text(
                title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        enabled = enabled
    )
}

@Composable
fun ButtonPrimaryYellowWithIcon(
    modifier: Modifier = Modifier,
    icon: Painter,
    title: String,
    onClick: () -> Unit,
    iconTint: Color? = null,
    enabled: Boolean = true,
) {
    ButtonPrimary(
        modifier = modifier,
        onClick = onClick,
        buttonColors = ButtonPrimaryDefaults.filledColors(
            background = ComposeAppTheme.colors.brandDefault,
            content = ComposeAppTheme.colors.buttonPrimaryBrandContent,
        ),
        content = {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = if (enabled) iconTint ?: LocalContentColor.current else ComposeAppTheme.colors.iconDisabled
            )
            HSpacer(width = 8.dp)
            Text(
                title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        enabled = enabled
    )
}

@Composable
fun ButtonPrimaryRed(
    modifier: Modifier = Modifier,
    title: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    ButtonPrimary(
        modifier = modifier,
        onClick = onClick,
        buttonColors = ButtonPrimaryDefaults.filledColors(
            background = ComposeAppTheme.colors.buttonPrimaryDestructiveBackground,
            content = ComposeAppTheme.colors.buttonPrimaryDestructiveContent,
        ),
        content = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        enabled = enabled
    )
}

@Composable
fun ButtonPrimaryYellowWithSpinner(
    modifier: Modifier = Modifier,
    title: String,
    onClick: () -> Unit,
    showSpinner: Boolean = false,
    enabled: Boolean = true
) {

    ButtonPrimary(
        modifier = modifier,
        onClick = onClick,
        buttonColors = ButtonPrimaryDefaults.filledColors(
            background = ComposeAppTheme.colors.brandDefault,
            content = ComposeAppTheme.colors.buttonPrimaryBrandContent,
        ),
        content = {
            if (showSpinner) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = ComposeAppTheme.colors.textSecondary,
                    strokeWidth = 2.dp
                )
            } else {
                Text(title)
            }
        },
        enabled = enabled
    )
}

@Composable
fun ButtonPrimaryWrapper(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    ProvideTextStyle(
        value = ComposeAppTheme.typography.headline2
    ) {
        Box(
            modifier = modifier
                .clip(ButtonPrimaryDefaults.Shape)
                .defaultMinSize(
                    minWidth = ButtonPrimaryDefaults.MinWidth,
                    minHeight = ButtonPrimaryDefaults.MinHeight
                )
                .clickable(
                    enabled = enabled,
                    onClick = onClick,
                )
                .padding(ButtonPrimaryDefaults.ContentPadding),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}


@Composable
fun ButtonPrimary(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonColors: HsButtonColors,
    enabled: Boolean = true,
    shape: Shape = ButtonPrimaryDefaults.Shape,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonPrimaryDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {

    Surface(
        modifier = modifier,
        shape = shape,
        color = buttonColors.backgroundColor(enabled).value,
        contentColor = buttonColors.contentColor(enabled).value,
        border = border,
        onClick = onClick,
        enabled = enabled,
    ) {
        ProvideTextStyle(
            value = ComposeAppTheme.typography.headline2
        ) {
            Row(
                Modifier
                    .defaultMinSize(
                        minWidth = ButtonPrimaryDefaults.MinWidth,
                        minHeight = ButtonPrimaryDefaults.MinHeight
                    )
                    .padding(contentPadding),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
    }
}

object ButtonPrimaryDefaults {
    private val ButtonHorizontalPadding = 16.dp

    val ContentPadding = PaddingValues(
        start = ButtonHorizontalPadding,
        end = ButtonHorizontalPadding
    )


    /**
     * The default min width applied for the button.
     * Note that you can override it by applying Modifier.widthIn directly on it.
     */
    val MinWidth = 50.dp

    /**
     * The default min height applied for the button.
     * Note that you can override it by applying Modifier.heightIn directly on it.
     */
    val MinHeight = 50.dp

    val Shape = RoundedCornerShape(12.dp)

    @Composable
    fun filledColors(background: Color, content: Color): HsButtonColors = HsButtonColors(
        backgroundColor = background,
        contentColor = content,
        disabledBackgroundColor = ComposeAppTheme.colors.buttonPrimaryDisabledBackground,
        disabledContentColor = ComposeAppTheme.colors.textDisabled,
    )

    @Composable
    fun textButtonColors(
        backgroundColor: Color,
        contentColor: Color,
        disabledBackgroundColor: Color,
        disabledContentColor: Color,
    ): HsButtonColors = HsButtonColors(
        backgroundColor = backgroundColor,
        contentColor = contentColor,
        disabledBackgroundColor = disabledBackgroundColor,
        disabledContentColor = disabledContentColor,
    )
}
