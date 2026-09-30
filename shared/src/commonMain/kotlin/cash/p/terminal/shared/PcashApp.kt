package cash.p.terminal.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import cash.p.terminal.shared.main.MainDestination
import cash.p.terminal.shared.main.MainDestinationTitle
import cash.p.terminal.shared.main.MainNavigation
import cash.p.terminal.shared.settings.MainSettingUiState
import cash.p.terminal.shared.settings.SettingsContent
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Composable
fun PcashApp(
    settingsUiState: MainSettingUiState,
    appVersion: String,
    modifier: Modifier = Modifier,
) {
    var selectedDestination by remember { mutableStateOf(MainDestination.Balance) }
    MainNavigation(
        selectedDestination = selectedDestination,
        onDestinationSelect = { selectedDestination = it },
        modifier = modifier.fillMaxSize(),
    ) { destination ->
        if (destination == MainDestination.Settings) {
            SettingsPane(settingsUiState, appVersion)
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(MainDestinationTitle(destination))
            }
        }
    }
}

@Composable
private fun SettingsPane(uiState: MainSettingUiState, appVersion: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ComposeAppTheme.colors.tyler),
    ) {
        Text(
            text = MainDestinationTitle(MainDestination.Settings),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            style = ComposeAppTheme.typography.title3,
            color = ComposeAppTheme.colors.leah,
        )
        SettingsContent(
            uiState = uiState,
            appVersion = appVersion,
            onAction = {},
            alertPainter = null,
            modifier = Modifier.weight(1f),
        )
    }
}
