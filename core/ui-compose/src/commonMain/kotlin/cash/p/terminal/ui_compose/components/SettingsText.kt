package cash.p.terminal.ui_compose.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Composable
fun BadgeText(
    text: String,
    modifier: Modifier = Modifier,
    background: Color = ComposeAppTheme.colors.lucian,
    textColor: Color = ComposeAppTheme.colors.white,
) {
    BadgeBase(
        background = background,
        content = {
            Text(
                text = text,
                color = textColor,
                style = ComposeAppTheme.typography.microSB,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        modifier = modifier,
    )
}

@Composable
fun B2(
    text: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    SettingsText(
        text = text,
        color = ComposeAppTheme.colors.leah,
        style = ComposeAppTheme.typography.body,
        modifier = modifier,
        textAlign = textAlign,
        overflow = overflow,
        maxLines = maxLines,
        onTextLayout = onTextLayout,
    )
}

@Composable
fun body_leah(
    text: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    B2(text, modifier, textAlign, overflow, maxLines, onTextLayout)
}

@Composable
fun C1(
    text: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    SettingsText(
        text = text,
        color = ComposeAppTheme.colors.grey,
        style = ComposeAppTheme.typography.subhead1,
        modifier = modifier,
        textAlign = textAlign,
        overflow = overflow,
        maxLines = maxLines,
        onTextLayout = onTextLayout,
    )
}

@Composable
fun subhead1_grey(
    text: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    C1(text, modifier, textAlign, overflow, maxLines, onTextLayout)
}

@Composable
fun C3(
    text: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    SettingsText(
        text = text,
        color = ComposeAppTheme.colors.jacob,
        style = ComposeAppTheme.typography.subhead1,
        modifier = modifier,
        textAlign = textAlign,
        overflow = overflow,
        maxLines = maxLines,
        onTextLayout = onTextLayout,
    )
}

@Composable
fun subhead1_jacob(
    text: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    C3(text, modifier, textAlign, overflow, maxLines, onTextLayout)
}

@Composable
fun F1(
    text: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    SettingsText(
        text = text,
        color = ComposeAppTheme.colors.grey,
        style = ComposeAppTheme.typography.caption,
        modifier = modifier,
        textAlign = textAlign,
        overflow = overflow,
        maxLines = maxLines,
        onTextLayout = onTextLayout,
    )
}

@Composable
fun caption_grey(
    text: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    F1(text, modifier, textAlign, overflow, maxLines, onTextLayout)
}

@Composable
private fun SettingsText(
    text: String,
    color: Color,
    style: TextStyle,
    textAlign: TextAlign?,
    overflow: TextOverflow,
    maxLines: Int,
    onTextLayout: (TextLayoutResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        textAlign = textAlign,
        overflow = overflow,
        maxLines = maxLines,
        onTextLayout = onTextLayout,
        style = style,
        color = color,
    )
}
