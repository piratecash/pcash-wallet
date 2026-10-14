package cash.p.terminal.ui_compose.components

import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

// M3 Switch is 52x32 dp; the app keeps the compact footprint of the former M2 switch.
private val M3SwitchWidth = 52.dp
private val M3SwitchHeight = 32.dp
private val SwitchWidth = 36.dp
// Former M2 target: 34x20 track plus 2 dp padding on each side.
private val TouchTargetWidth = 38.dp
private val TouchTargetHeight = 24.dp

@Composable
fun HsSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
){
    val colors = SwitchDefaults.colors(
        checkedThumbColor = ComposeAppTheme.colors.switchThumbOn,
        uncheckedThumbColor = ComposeAppTheme.colors.switchThumbOff,
        checkedTrackColor = ComposeAppTheme.colors.switchTrackOn,
        uncheckedTrackColor = ComposeAppTheme.colors.switchTrackOff,
        checkedBorderColor = ComposeAppTheme.colors.transparent,
        uncheckedBorderColor = ComposeAppTheme.colors.transparent,
        disabledCheckedThumbColor = ComposeAppTheme.colors.switchThumbOn,
        disabledUncheckedThumbColor = ComposeAppTheme.colors.switchThumbOff,
        disabledCheckedTrackColor = ComposeAppTheme.colors.switchTrackOn,
        disabledUncheckedTrackColor = ComposeAppTheme.colors.switchTrackOff,
        disabledCheckedBorderColor = ComposeAppTheme.colors.transparent,
        disabledUncheckedBorderColor = ComposeAppTheme.colors.transparent,
    )
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Box(
            modifier = modifier
                .size(TouchTargetWidth, TouchTargetHeight)
                .let { if (enabled) it else it.alpha(DisabledControlAlpha) }
                .let {
                    if (onCheckedChange == null) it else it.toggleable(
                        value = checked,
                        onValueChange = onCheckedChange,
                        enabled = enabled,
                        role = Role.Switch
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Switch(
                modifier = Modifier
                    .requiredSize(M3SwitchWidth, M3SwitchHeight)
                    .scale(SwitchWidth / M3SwitchWidth),
                checked = checked,
                onCheckedChange = null,
                colors = colors,
                enabled = enabled
            )
        }
    }
}
