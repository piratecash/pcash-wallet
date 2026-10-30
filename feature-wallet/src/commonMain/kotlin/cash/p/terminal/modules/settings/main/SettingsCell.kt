package cash.p.terminal.modules.settings.main

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_arrow_right
import cash.p.terminal.resources.ic_attention_red_20
import cash.p.terminal.ui_compose.components.HsSettingCell
import cash.p.terminal.ui_compose.components.tinted
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** The mark on a settings row that needs the user's attention. */
@Composable
fun settingsAlertPainter(): Painter =
    painterResource(Res.drawable.ic_attention_red_20).tinted(ComposeAppTheme.colors.statusError)

@Composable
fun SettingsCell(
    title: StringResource,
    onClick: (() -> Unit)?,
    icon: DrawableResource? = null,
    iconTint: Color? = null,
    value: String? = null,
    counterBadge: String? = null,
    newBadgeText: String? = null,
    alertPainter: Painter? = null,
) {
    HsSettingCell(
        title = stringResource(title),
        arrowPainter = onClick?.let { painterResource(Res.drawable.ic_arrow_right) },
        leadingPainter = icon?.let { painterResource(it) },
        iconTint = iconTint,
        value = value,
        counterBadge = counterBadge,
        newBadgeText = newBadgeText,
        alertPainter = alertPainter,
        onClick = onClick,
    )
}
