@file:JvmName("SharedHsSettingCellKt")

package cash.p.terminal.ui_compose.components

import kotlin.jvm.JvmName

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import androidx.compose.material3.Text
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_arrow_right
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun HsSettingCell(
    title: String,
    arrowPainter: Painter?,
    modifier: Modifier = Modifier,
    leadingPainter: Painter? = null,
    iconTint: Color? = null,
    value: String? = null,
    counterBadge: String? = null,
    newBadgeText: String? = null,
    alertPainter: Painter? = null,
    onClick: (() -> Unit)? = null,
) {
    RowUniversal(
        modifier = modifier.padding(horizontal = 16.dp),
        onClick = onClick,
    ) {
        SettingCellLeadingIcon(leadingPainter, iconTint)
        SettingCellTitle(title, leadingPainter != null)
        Spacer(Modifier.weight(1f))
        SettingCellValue(counterBadge, value, onClick != null)
        SettingCellNewBadge(newBadgeText)
        SettingCellTrailingIcons(alertPainter, arrowPainter)
    }
}

@Composable
fun HsSettingCell(
    title: StringResource,
    icon: DrawableResource? = null,
    iconTint: Color? = null,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
) = EnableableSettingCell(stringResource(title), icon?.let { painterResource(it) }, iconTint, value, onClick, enabled)

@Composable
internal fun EnableableSettingCell(
    title: String,
    iconPainter: Painter?,
    iconTint: Color?,
    value: String?,
    onClick: (() -> Unit)?,
    enabled: Boolean,
) {
    val primaryColor = if (enabled) ComposeAppTheme.colors.textPrimary else ComposeAppTheme.colors.textDisabled
    val secondaryColor = if (enabled) ComposeAppTheme.colors.textSecondary else ComposeAppTheme.colors.textDisabled
    val iconColor = if (enabled) {
        iconTint ?: ComposeAppTheme.colors.iconSecondary
    } else {
        ComposeAppTheme.colors.iconDisabled
    }

    RowUniversal(
        modifier = Modifier.padding(horizontal = 16.dp),
        onClick = onClick,
        enabled = enabled,
    ) {
        iconPainter?.let {
            Icon(
                modifier = Modifier.size(24.dp),
                painter = it,
                contentDescription = null,
                tint = iconColor,
            )
        }
        Text(
            text = title,
            color = primaryColor,
            style = ComposeAppTheme.typography.body,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = if (iconPainter != null) 16.dp else 0.dp, end = 16.dp),
        )
        Spacer(Modifier.weight(1f))

        if (value != null) {
            Text(
                text = value,
                color = secondaryColor,
                style = ComposeAppTheme.typography.subhead1,
                maxLines = 1,
                modifier = Modifier.padding(
                    horizontal = if (onClick != null) 8.dp else 0.dp
                ),
            )
        }

        if (onClick != null) {
            Icon(
                modifier = Modifier.size(20.dp),
                painter = painterResource(Res.drawable.ic_arrow_right),
                contentDescription = null,
                tint = iconColor,
            )
        }
    }
}

@Composable
private fun SettingCellLeadingIcon(
    leadingPainter: Painter?,
    iconTint: Color?,
) {
    leadingPainter?.let { painter ->
        Icon(
            modifier = Modifier.size(24.dp),
            painter = painter,
            contentDescription = null,
            tint = iconTint ?: ComposeAppTheme.colors.iconSecondary,
        )
    }
}

@Composable
private fun SettingCellTitle(
    title: String,
    hasLeadingIcon: Boolean,
) {
    body_leah(
        text = title,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(
            start = if (hasLeadingIcon) 16.dp else 0.dp,
            end = 16.dp,
        ),
    )
}

@Composable
private fun SettingCellValue(
    counterBadge: String?,
    value: String?,
    clickable: Boolean,
) {
    when {
        counterBadge != null -> BadgeText(
            text = counterBadge,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        value != null -> subhead1_grey(
            text = value,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = if (clickable) 8.dp else 0.dp),
        )
    }
}

@Composable
private fun SettingCellNewBadge(text: String?) {
    text?.let {
        BadgeText(
            text = it,
            modifier = Modifier.padding(horizontal = 8.dp),
            background = ComposeAppTheme.colors.brandDefault,
        )
    }
}

@Composable
private fun RowScope.SettingCellTrailingIcons(
    alertPainter: Painter?,
    arrowPainter: Painter?,
) {
    alertPainter?.let {
        SettingCellImage(it)
        Spacer(Modifier.width(12.dp))
    }
    arrowPainter?.let { SettingCellImage(it) }
}

@Composable
private fun SettingCellImage(painter: Painter) {
    Image(
        modifier = Modifier.size(20.dp),
        painter = painter,
        contentDescription = null,
    )
}
