package cash.p.terminal.ui_compose.components

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonColors
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material.ProvideTextStyle
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.R
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Composable
fun ButtonPrimaryDefaultWithIcon(
    modifier: Modifier = Modifier,
    icon: Int,
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
                painter = painterResource(icon),
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
            background = ComposeAppTheme.colors.brand,
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
    icon: Int,
    title: String,
    onClick: () -> Unit,
    iconTint: Color? = null,
    enabled: Boolean = true,
) {
    ButtonPrimary(
        modifier = modifier,
        onClick = onClick,
        buttonColors = ButtonPrimaryDefaults.filledColors(
            background = ComposeAppTheme.colors.brand,
            content = ComposeAppTheme.colors.buttonPrimaryBrandContent,
        ),
        content = {
            Icon(
                painter = painterResource(icon),
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
fun ButtonPrimaryNeutral(
    modifier: Modifier = Modifier,
    title: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    ButtonPrimary(
        modifier = modifier,
        onClick = onClick,
        buttonColors = ButtonPrimaryDefaults.textButtonColors(
            backgroundColor = ComposeAppTheme.colors.steelLight,
            contentColor = ComposeAppTheme.colors.dark,
            disabledBackgroundColor = ComposeAppTheme.colors.steel20,
            disabledContentColor = ComposeAppTheme.colors.textDisabled,
        ),
        content = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        enabled = enabled
    )
}

@Composable
fun ButtonPrimaryLight(
    modifier: Modifier = Modifier,
    title: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    ButtonPrimary(
        modifier = modifier,
        onClick = onClick,
        buttonColors = ButtonPrimaryDefaults.textButtonColors(
            backgroundColor = ComposeAppTheme.colors.bran,
            contentColor = ComposeAppTheme.colors.claude,
            disabledBackgroundColor = ComposeAppTheme.colors.steel20,
            disabledContentColor = ComposeAppTheme.colors.textDisabled,
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
            background = ComposeAppTheme.colors.brand,
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


@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ButtonPrimary(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonColors: ButtonColors,
    enabled: Boolean = true,
    shape: Shape = ButtonPrimaryDefaults.Shape,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonPrimaryDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {

    val contentColor = buttonColors.contentColor(enabled).value
    Surface(
        modifier = modifier,
        shape = shape,
        color = buttonColors.backgroundColor(enabled).value,
        contentColor = contentColor,
        border = border,
        onClick = onClick,
        enabled = enabled,
    ) {
        // M2 Surface sets only the M2 content colour; M3 icons inside read their own local.
        CompositionLocalProvider(LocalContentColor provides contentColor) {
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
}

object ButtonPrimaryDefaults {
    private val ButtonHorizontalPadding = 16.dp

    val ContentPadding = PaddingValues(
        start = ButtonHorizontalPadding,
        end = ButtonHorizontalPadding
    )


    /**
     * The default min width applied for the [Button].
     * Note that you can override it by applying Modifier.widthIn directly on [Button].
     */
    val MinWidth = 50.dp

    /**
     * The default min width applied for the [Button].
     * Note that you can override it by applying Modifier.heightIn directly on [Button].
     */
    val MinHeight = 50.dp

    val Shape = RoundedCornerShape(12.dp)

    @Composable
    fun filledColors(background: Color, content: Color): ButtonColors = HsButtonColors(
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
    ): ButtonColors = HsButtonColors(
        backgroundColor = backgroundColor,
        contentColor = contentColor,
        disabledBackgroundColor = disabledBackgroundColor,
        disabledContentColor = disabledContentColor,
    )
}

// region Previews

@Preview(name = "Light", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ButtonPrimaryDefaultPreview() {
    ComposeAppTheme {
        Column(
            modifier = Modifier
                .background(ComposeAppTheme.colors.tyler)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ButtonPrimaryDefault(
                title = "Primary Default",
                onClick = {}
            )
            ButtonPrimaryDefault(
                title = "Primary Default Disabled",
                onClick = {},
                enabled = false
            )
        }
    }
}

@Preview(name = "Light", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ButtonPrimaryDefaultWithIconPreview() {
    ComposeAppTheme {
        Column(
            modifier = Modifier
                .background(ComposeAppTheme.colors.tyler)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ButtonPrimaryDefaultWithIcon(
                icon = R.drawable.ic_back,
                title = "With Icon",
                onClick = {}
            )
            ButtonPrimaryDefaultWithIcon(
                icon = R.drawable.ic_back,
                title = "With Tinted Icon",
                iconTint = ComposeAppTheme.colors.brand,
                onClick = {}
            )
            ButtonPrimaryDefaultWithIcon(
                icon = R.drawable.ic_back,
                title = "With Icon Disabled",
                onClick = {},
                enabled = false
            )
        }
    }
}

@Preview(name = "Light", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ButtonPrimaryTransparentPreview() {
    ComposeAppTheme {
        Column(
            modifier = Modifier
                .background(ComposeAppTheme.colors.tyler)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ButtonPrimaryTransparent(
                title = "Transparent",
                onClick = {}
            )
            ButtonPrimaryTransparent(
                title = "Transparent Disabled",
                onClick = {},
                enabled = false
            )
        }
    }
}

@Preview(name = "Light", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ButtonPrimaryYellowPreview() {
    ComposeAppTheme {
        Column(
            modifier = Modifier
                .background(ComposeAppTheme.colors.tyler)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ButtonPrimaryYellow(
                title = "Yellow Button",
                onClick = {}
            )
            ButtonPrimaryYellow(
                title = "Yellow Loading",
                onClick = {},
                loadingIndicator = true
            )
            ButtonPrimaryYellow(
                title = "Yellow Disabled",
                onClick = {},
                enabled = false
            )
        }
    }
}

@Preview(name = "Light", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ButtonPrimaryYellowWithIconPreview() {
    ComposeAppTheme {
        Column(
            modifier = Modifier
                .background(ComposeAppTheme.colors.tyler)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ButtonPrimaryYellowWithIcon(
                icon = R.drawable.ic_back,
                title = "Yellow With Icon",
                onClick = {}
            )
            ButtonPrimaryYellowWithIcon(
                icon = R.drawable.ic_back,
                title = "Yellow With Tint",
                iconTint = ComposeAppTheme.colors.lucian,
                onClick = {}
            )
            ButtonPrimaryYellowWithIcon(
                icon = R.drawable.ic_back,
                title = "Disabled",
                onClick = {},
                enabled = false
            )
        }
    }
}

@Preview(name = "Light", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ButtonPrimaryRedPreview() {
    ComposeAppTheme {
        Column(
            modifier = Modifier
                .background(ComposeAppTheme.colors.tyler)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ButtonPrimaryRed(
                title = "Red Button",
                onClick = {}
            )
            ButtonPrimaryRed(
                title = "Red Disabled",
                onClick = {},
                enabled = false
            )
        }
    }
}

@Preview(name = "Light", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ButtonPrimaryNeutralPreview() {
    ComposeAppTheme {
        Column(
            modifier = Modifier
                .background(ComposeAppTheme.colors.tyler)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ButtonPrimaryNeutral(
                title = "Neutral Button",
                onClick = {}
            )
            ButtonPrimaryNeutral(
                title = "Neutral Disabled",
                onClick = {},
                enabled = false
            )
        }
    }
}

@Preview(name = "Light", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ButtonPrimaryYellowWithSpinnerPreview() {
    ComposeAppTheme {
        Column(
            modifier = Modifier
                .background(ComposeAppTheme.colors.tyler)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ButtonPrimaryYellowWithSpinner(
                title = "Yellow Spinner",
                onClick = {},
                showSpinner = false
            )
            ButtonPrimaryYellowWithSpinner(
                title = "Showing Spinner",
                onClick = {},
                showSpinner = true
            )
            ButtonPrimaryYellowWithSpinner(
                title = "Spinner Disabled",
                onClick = {},
                enabled = false
            )
        }
    }
}

// endregion
