package cash.p.terminal.modules.backupalert

import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.PageResumeEffect
import kotlinx.coroutines.delay

@Composable
fun BackupAlert(navigation: HSNavigation) {
    val viewModel = koinViewModel<BackupAlertViewModel>()

    // Not LifecycleResumeEffect: the page must stay resumed under a bottom sheet, as the fragment did.
    PageResumeEffect(onResume = viewModel::resume, onPause = viewModel::pause)

    val account = viewModel.account
    if (account != null && account.supportsBackup) {
        LaunchedEffect(account) {
            delay(300)
            viewModel.onHandled()
            navigation.slideFromBottom(BackupRecoveryPhraseSheet(account))
        }
    }
}
