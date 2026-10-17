package cash.p.terminal.shared.desktop.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import cash.p.terminal.modules.settings.security.autolock.AutoLockInterval
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.shared.settings.AutoLockIntervalsContent
import io.horizontalsystems.core.DesktopSettingsStore
import org.koin.compose.viewmodel.koinViewModel

class DesktopAutoLockIntervalsPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel: DesktopAutoLockIntervalsViewModel = koinViewModel()
        AutoLockIntervalsContent(
            selected = viewModel.selectedInterval,
            onSelect = { interval ->
                viewModel.onSelectAutoLockInterval(interval)
                navigation.navigateUpSafely()
            },
            onBack = navigation::navigateUpSafely,
        )
    }
}

class DesktopAutoLockIntervalsViewModel(
    private val settingsStore: DesktopSettingsStore,
) : ViewModel() {

    var selectedInterval by mutableStateOf(settingsStore.autoLockInterval)
        private set

    fun onSelectAutoLockInterval(interval: AutoLockInterval) {
        settingsStore.autoLockInterval = interval
        selectedInterval = interval
    }
}
