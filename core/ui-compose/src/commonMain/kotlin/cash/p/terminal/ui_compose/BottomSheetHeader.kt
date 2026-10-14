package cash.p.terminal.ui_compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.ui_compose.components.BottomSheetSurface
import cash.p.terminal.ui_compose.components.HsIconButton
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_close_24
import org.jetbrains.compose.resources.painterResource

@Composable
fun BottomSheetHeader(
    title: String,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconPainter: Painter? = null,
    titleColor: Color = ComposeAppTheme.colors.textPrimary,
    iconTint: ColorFilter? = null,
    content: @Composable() (ColumnScope.() -> Unit),
) {
    BottomSheetHeader(
        iconPainter = iconPainter,
        titleContent = {
            Text(
                text = title,
                modifier = Modifier
                    .padding(
                        start = if (iconPainter != null) 16.dp else 0.dp,
                        end = 16.dp
                    )
                    .weight(1f)
                    .align(Alignment.CenterVertically),
                maxLines = 1,
                style = ComposeAppTheme.typography.headline2,
                color = titleColor,
            )
        },
        onCloseClick = onCloseClick,
        iconTint = iconTint,
        modifier = modifier,
        content = content
    )
}

@Composable
fun BottomSheetHeaderMultiline(
    iconPainter: Painter,
    title: String,
    subtitle: String,
    onCloseClick: () -> Unit,
    iconTint: ColorFilter? = null,
    content: @Composable() (ColumnScope.() -> Unit),
) {
    BottomSheetHeader(
        iconPainter = iconPainter,
        titleContent = {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .weight(1f)
                    .align(Alignment.CenterVertically),
            ) {
                body_leah(
                    text = title,
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.height(1.dp))
                subhead2_grey(
                    text = subtitle,
                    maxLines = 1,
                )
            }
        },
        onCloseClick = onCloseClick,
        iconTint = iconTint,
        content = content
    )
}

@Composable
private fun BottomSheetHeader(
    iconPainter: Painter? ,
    titleContent: @Composable() (RowScope.() -> Unit),
    onCloseClick: () -> Unit,
    iconTint: ColorFilter?,
    modifier: Modifier = Modifier,
    content: @Composable() (ColumnScope.() -> Unit)
) {
    BottomSheetSurface(modifier = modifier) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Row(
                modifier = Modifier
                    .padding(start = 32.dp, top = 24.dp, end = 32.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                iconPainter?.let {
                    Image(
                        modifier = Modifier.size(24.dp),
                        painter = iconPainter,
                        colorFilter = iconTint,
                        contentDescription = null
                    )
                }
                titleContent.invoke(this)
                HsIconButton(
                    modifier = Modifier.size(24.dp),
                    onClick = onCloseClick
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_close_24),
                        tint = ComposeAppTheme.colors.iconSecondary,
                        contentDescription = null,
                    )
                }
            }
            Column(
                content = content
            )
        }
    }
}
