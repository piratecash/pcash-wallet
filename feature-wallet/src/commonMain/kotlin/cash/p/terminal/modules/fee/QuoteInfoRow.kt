package cash.p.terminal.modules.fee

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.CellUniversal

@Composable
fun QuoteInfoRow(
    borderTop: Boolean = false,
    title: @Composable() (RowScope.() -> Unit),
    value: @Composable() (RowScope.() -> Unit),
) {
    CellUniversal(borderTop = borderTop) {
        title.invoke(this)
        HSpacer(width = 16.dp)
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
            value.invoke(this)
        }
    }
}
