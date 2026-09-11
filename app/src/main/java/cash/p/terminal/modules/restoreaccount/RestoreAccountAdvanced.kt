package cash.p.terminal.modules.restoreaccount

import androidx.compose.runtime.Composable
import cash.p.terminal.modules.restoreaccount.restoremenu.RestoreMenuModule.RestoreOption

@Composable
fun AdvancedRestoreScreen(
    restoreOption: RestoreOption,
    recoveryPhrase: @Composable () -> Unit,
    privateKey: @Composable () -> Unit,
) {
    when (restoreOption) {
        RestoreOption.RecoveryPhrase -> recoveryPhrase()
        RestoreOption.PrivateKey -> privateKey()
    }
}
