package cash.p.terminal.ui_compose.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.icon_24_warning_2
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.painterResource

@Composable
fun SwitchWithText(
    text: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: ((Boolean) -> Unit)?,
    extraIcon: (@Composable () -> Unit)? = null,
    borderTop: Boolean = false,
) {
    CellUniversal(borderTop = borderTop) {
        if (enabled) {
            body_leah(
                text = text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            )
        } else {
            body_disabled(
                text = text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            )
        }
        extraIcon?.invoke()
        HsSwitch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun SwitchWithTextWarning(
    text: String,
    checked: Boolean,
    enabled: Boolean = true,
    showWarning: Boolean = true,
    onWarningIconClick: () -> Unit,
    onCheckedChange: ((Boolean) -> Unit)?,
    borderTop: Boolean = false,
) {
    SwitchWithText(
        text = text,
        checked = checked,
        enabled = enabled,
        borderTop = borderTop,
        extraIcon = if (showWarning) {
            {
                Icon(
                    painter = painterResource(Res.drawable.icon_24_warning_2),
                    contentDescription = null,
                    tint = ComposeAppTheme.colors.statusError,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(20.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(
                                bounded = false,
                                radius = 20.dp
                            ),
                            onClick = onWarningIconClick
                        )
                )
            }
        } else null,
        onCheckedChange = onCheckedChange
    )
}
