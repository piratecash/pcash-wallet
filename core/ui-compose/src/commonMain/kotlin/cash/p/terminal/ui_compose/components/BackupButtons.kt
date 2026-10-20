package cash.p.terminal.ui_compose.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cash.p.terminal.resources.BackupRecoveryPhrase_LocalBackup
import cash.p.terminal.resources.BackupRecoveryPhrase_ManualBackup
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_edit_24
import cash.p.terminal.resources.ic_file_24
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun BackupButtons(
    onManualBackupClick: () -> Unit,
    onLocalBackupClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ButtonPrimaryYellowWithIcon(
        modifier = modifier.fillMaxWidth(),
        title = stringResource(Res.string.BackupRecoveryPhrase_ManualBackup),
        icon = painterResource(Res.drawable.ic_edit_24),
        onClick = onManualBackupClick
    )

    VSpacer(12.dp)

    ButtonPrimaryDefaultWithIcon(
        modifier = modifier.fillMaxWidth(),
        title = stringResource(Res.string.BackupRecoveryPhrase_LocalBackup),
        icon = painterResource(Res.drawable.ic_file_24),
        onClick = onLocalBackupClick
    )
}
