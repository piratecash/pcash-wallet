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
private fun SettingCellLeadingIcon(
    leadingPainter: Painter?,
    iconTint: Color?,
) {
    leadingPainter?.let { painter ->
        Icon(
            modifier = Modifier.size(24.dp),
            painter = painter,
            contentDescription = null,
            tint = iconTint ?: ComposeAppTheme.colors.grey,
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
            background = ComposeAppTheme.colors.issykBlue,
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
