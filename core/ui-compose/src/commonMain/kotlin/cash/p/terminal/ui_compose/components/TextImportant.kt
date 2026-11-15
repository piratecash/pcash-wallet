package cash.p.terminal.ui_compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_attention_20
import cash.p.terminal.resources.ic_close_24
import cash.p.terminal.resources.ic_info_20
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Composable
fun TextImportantWarning(
    modifier: Modifier = Modifier,
    text: String,
    title: String? = null,
    icon: DrawableResource? = null,
    onClose: (() -> Unit)? = null
) {
    TextImportant(
        modifier = modifier,
        text = text,
        title = title,
        icon = icon,
        borderColor = ComposeAppTheme.colors.statusWarning,
        backgroundColor = ComposeAppTheme.colors.statusWarning20,
        textColor = ComposeAppTheme.colors.statusWarning,
        iconColor = ComposeAppTheme.colors.statusWarning,
        onClose = onClose
    )
}

@Composable
fun TextImportantWarning(
    modifier: Modifier = Modifier,
    text: AnnotatedString,
    title: String? = null,
    icon: DrawableResource? = null,
    onClose: (() -> Unit)? = null
) {
    TextImportant(
        modifier = modifier,
        text = text,
        title = title,
        icon = icon,
        borderColor = ComposeAppTheme.colors.statusWarning,
        backgroundColor = ComposeAppTheme.colors.statusWarning20,
        textColor = ComposeAppTheme.colors.statusWarning,
        iconColor = ComposeAppTheme.colors.statusWarning,
        onClose = onClose
    )
}

@Composable
fun TextImportantWarning(
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: DrawableResource? = null,
    onClose: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    TextImportant(
        modifier = modifier,
        title = title,
        icon = icon,
        borderColor = ComposeAppTheme.colors.statusWarning,
        backgroundColor = ComposeAppTheme.colors.statusWarning20,
        textColor = ComposeAppTheme.colors.statusWarning,
        iconColor = ComposeAppTheme.colors.statusWarning,
        onClose = onClose,
        alignTrailingToEndWhenNoTitle = true,
        content = content
    )
}

/** Borderless warning: the icon sits beside the text, the text keeps the primary color. */
@Composable
fun TextImportantWarningInline(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ComposeAppTheme.colors.statusWarning20)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_attention_20),
            contentDescription = null,
            tint = ComposeAppTheme.colors.statusWarning,
        )
        Text(
            text = text,
            color = ComposeAppTheme.colors.textPrimary,
            style = ComposeAppTheme.typography.subhead2,
        )
    }
}

@Preview
@Composable
private fun TextImportantWarningInlinePreview() {
    ComposeAppTheme {
        TextImportantWarningInline(
            text = "After the transfer the domain is unlinked from your wallet. Transfers to piratecash.ton " +
                "will go to the new owner.",
        )
    }
}

@Composable
fun TextImportantInfo(
    text: String,
    modifier: Modifier = Modifier,
) = TextImportantInfo(AnnotatedString(text), modifier)

@Composable
fun TextImportantInfo(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
) {
    // The `icon` parameter belongs to the title row; here the icon sits beside the text.
    TextImportant(
        modifier = modifier,
        borderColor = ComposeAppTheme.colors.textDisabled,
        backgroundColor = ComposeAppTheme.colors.surfaceElevated,
        textColor = ComposeAppTheme.colors.textPrimary,
        iconColor = ComposeAppTheme.colors.iconSecondary,
    ) {
        Row {
            Icon(
                painter = painterResource(Res.drawable.ic_info_20),
                contentDescription = null,
                tint = ComposeAppTheme.colors.iconSecondary
            )
            Spacer(Modifier.width(12.dp))
            subhead2_leah(text = text)
        }
    }
}

@Composable
fun TextImportantError(
    modifier: Modifier = Modifier,
    text: String,
    title: String? = null,
    icon: DrawableResource? = null,
    onInfoClick: (() -> Unit)? = null,
) {
    TextImportant(
        modifier = modifier,
        text = text,
        title = title,
        icon = icon,
        borderColor = ComposeAppTheme.colors.statusError,
        backgroundColor = ComposeAppTheme.colors.statusError20,
        textColor = ComposeAppTheme.colors.statusError,
        iconColor = ComposeAppTheme.colors.statusError,
        onInfoClick = onInfoClick,
    )
}

@Composable
fun TextImportant(
    modifier: Modifier = Modifier,
    text: String,
    title: String? = null,
    icon: DrawableResource? = null,
    borderColor: Color,
    backgroundColor: Color,
    textColor: Color,
    iconColor: Color,
    onClose: (() -> Unit)? = null,
    onInfoClick: (() -> Unit)? = null,
) {
    TextImportant(
        modifier = modifier,
        title = title,
        icon = icon,
        borderColor = borderColor,
        backgroundColor = backgroundColor,
        textColor = textColor,
        iconColor = iconColor,
        onClose = onClose,
        onInfoClick = onInfoClick
    ) {
        if (text.isNotEmpty()) {
            subhead2_leah(text = text)
        }
    }
}

@Composable
fun TextImportant(
    modifier: Modifier = Modifier,
    text: AnnotatedString,
    title: String? = null,
    icon: DrawableResource? = null,
    borderColor: Color,
    backgroundColor: Color,
    textColor: Color,
    iconColor: Color,
    onClose: (() -> Unit)? = null
) {
    TextImportant(
        modifier = modifier,
        title = title,
        icon = icon,
        borderColor = borderColor,
        backgroundColor = backgroundColor,
        textColor = textColor,
        iconColor = iconColor,
        onClose = onClose,
        alignTrailingToEndWhenNoTitle = true
    ) {
        if (text.isNotEmpty()) {
            subhead2_leah(text = text)
        }
    }
}

@Composable
fun TextImportant(
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: DrawableResource? = null,
    borderColor: Color,
    backgroundColor: Color,
    textColor: Color,
    iconColor: Color,
    onClose: (() -> Unit)? = null,
    onInfoClick: (() -> Unit)? = null,
    alignTrailingToEndWhenNoTitle: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    TextImportantContainer(
        modifier = modifier,
        title = title,
        icon = icon,
        borderColor = borderColor,
        backgroundColor = backgroundColor,
        textColor = textColor,
        iconColor = iconColor,
        onClose = onClose,
        onInfoClick = onInfoClick,
        alignTrailingToEndWhenNoTitle = alignTrailingToEndWhenNoTitle,
        content = content
    )
}

@Composable
private fun TextImportantContainer(
    modifier: Modifier,
    title: String?,
    icon: DrawableResource?,
    borderColor: Color,
    backgroundColor: Color,
    textColor: Color,
    iconColor: Color,
    onClose: (() -> Unit)?,
    onInfoClick: (() -> Unit)?,
    alignTrailingToEndWhenNoTitle: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (title != null || icon != null || onInfoClick != null || onClose != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                icon?.let {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        tint = iconColor
                    )
                    Spacer(Modifier.width(12.dp))
                }
                title?.let {
                    Text(
                        text = it,
                        color = textColor,
                        style = ComposeAppTheme.typography.subhead1,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (title == null && alignTrailingToEndWhenNoTitle) {
                    Spacer(modifier = Modifier.weight(1f))
                }
                onInfoClick?.let { infoClick ->
                    Spacer(Modifier.width(12.dp))
                    HsIconButton(
                        modifier = Modifier.size(20.dp),
                        onClick = infoClick,
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_info_20),
                            contentDescription = null,
                            tint = ComposeAppTheme.colors.iconSecondary
                        )
                    }
                }
                onClose?.let { close ->
                    Spacer(modifier = Modifier.size(12.dp))
                    Icon(
                        painter = painterResource(Res.drawable.ic_close_24),
                        contentDescription = null,
                        tint = ComposeAppTheme.colors.iconSecondary,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = close
                            )
                    )
                }
            }
        }
        content()
    }
}
