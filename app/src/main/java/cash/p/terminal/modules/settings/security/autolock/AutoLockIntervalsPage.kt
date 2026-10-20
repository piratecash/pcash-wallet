package cash.p.terminal.modules.settings.security.autolock

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.shared.settings.AutoLockIntervalsContent

class AutoLockIntervalsPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        AutoLockIntervalsScreen(
            close = { navigation.navigateUpSafely() },
        )
    }

}

@Composable
private fun AutoLockIntervalsScreen(
    close: () -> Unit,
    viewModel: AutoLockIntervalsViewModel = viewModel(
        factory = AutoLockModule.Factory()
    )
) {
    AutoLockIntervalsContent(
        selected = viewModel.selectedInterval,
        onSelect = { interval ->
            viewModel.onSelectAutoLockInterval(interval)
            close()
        },
        onBack = close,
    )
}
