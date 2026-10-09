package cash.p.terminal.ui_compose.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.R

@Composable
fun BackupButtons(
    onManualBackupClick: () -> Unit,
    onLocalBackupClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ButtonPrimaryYellowWithIcon(
        modifier = modifier.fillMaxWidth(),
        title = stringResource(R.string.BackupRecoveryPhrase_ManualBackup),
        icon = R.drawable.ic_edit_24,
        onClick = onManualBackupClick
    )

    VSpacer(12.dp)

    ButtonPrimaryDefaultWithIcon(
        modifier = modifier.fillMaxWidth(),
        title = stringResource(R.string.BackupRecoveryPhrase_LocalBackup),
        icon = R.drawable.ic_file_24,
        onClick = onLocalBackupClick
    )
}
