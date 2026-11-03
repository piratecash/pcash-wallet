package cash.p.terminal.modules.send.offline

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HsSettingCell
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_send_24
import cash.p.terminal.resources.offline_transaction_sign_offline

@Composable
internal fun OfflineSignActionCell(
    supported: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!supported) return

    Box(modifier = modifier.padding(top = 12.dp)) {
        CellUniversalLawrenceSection {
            HsSettingCell(
                title = Res.string.offline_transaction_sign_offline,
                icon = Res.drawable.ic_send_24,
                onClick = onClick,
                enabled = enabled,
            )
        }
    }
}
