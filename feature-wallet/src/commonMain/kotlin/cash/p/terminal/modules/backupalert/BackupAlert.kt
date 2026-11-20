package cash.p.terminal.modules.backupalert

import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import cash.p.terminal.feature.wallet.rememberSecretAction
import cash.p.terminal.modules.manageaccount.shamirbackup.ShamirBackupIntroPage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.PageResumeEffect
import cash.p.terminal.navigation.rememberResultLauncher
import cash.p.terminal.navigation.slideFromBottom
import kotlinx.coroutines.delay

@Composable
fun BackupAlert(navigation: HSNavigation) {
    val viewModel = koinViewModel<BackupAlertViewModel>()
    val openAuthorized = navigation.rememberSecretAction(navigation::slideFromBottom)
    val sheetLauncher = navigation.rememberResultLauncher<BackupRecoveryPhraseSheet.ShamirBackupRequested> {
        openAuthorized(ShamirBackupIntroPage(it.accountId))
    }

    // Not LifecycleResumeEffect: the page must stay resumed under a bottom sheet, as the fragment did.
    PageResumeEffect(onResume = viewModel::resume, onPause = viewModel::pause)

    val account = viewModel.account
    if (account != null && account.supportsBackup) {
        LaunchedEffect(account) {
            delay(300)
            viewModel.onHandled()
            sheetLauncher.slideFromBottom(BackupRecoveryPhraseSheet(account))
        }
    }
}
