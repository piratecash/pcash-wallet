package cash.p.terminal.ui_compose.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun HsRadioButton(
    modifier: Modifier = Modifier,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    HsIconButton(
        modifier = modifier,
        onClick = onClick,
        enabled = enabled,
    ) {
        SelectionIndicator(selected = selected, enabled = enabled)
    }
}
