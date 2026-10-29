package cash.p.terminal.modules.multiswap.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cash.p.terminal.R
import cash.p.terminal.modules.evmfee.ButtonsGroupWithShade
import cash.p.terminal.modules.multiswap.SwapViewModel
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Composable
fun SwapSettingsScreen(
    navigation: HSNavigation,
    swapViewModel: SwapViewModel
) {
    val viewModel =
        viewModel<SwapSettingsViewModel>(factory = SwapSettingsViewModel.Factory(swapViewModel.getSettings()))

    val uiState = viewModel.uiState
    val settings = swapViewModel.uiState.quote?.swapQuote?.settings
    val customTitle = settings?.takeIf { it.size == 1 }?.firstOrNull()?.titleRes
    val keyboardController = LocalSoftwareKeyboardController.current

    val applySettings: () -> Unit = {
        keyboardController?.hide()
        swapViewModel.onUpdateSettings(viewModel.getSettings())
        navigation.navigateUpSafely()
    }

    Scaffold(
        topBar = {
            AppBar(
                title = stringResource(customTitle ?: R.string.SwapSettings_Title),
                navigationIcon = {
                    HsBackButton(onClick = navigation::navigateUpSafely)
                },
            )
        },
        bottomBar = {
            ButtonsGroupWithShade(modifier = Modifier.navigationBarsPadding()) {
                ButtonPrimaryYellow(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth(),
                    title = stringResource(id = R.string.SwapSettings_Apply),
                    enabled = uiState.applyEnabled,
                    onClick = applySettings
                )
            }
        },
        containerColor = ComposeAppTheme.colors.backgroundBase,
    ) {
        Column(modifier = Modifier.padding(it)) {
            LazyColumn(modifier = Modifier.weight(1f)) {
                item {
                    VSpacer(height = 12.dp)
                }

                settings?.forEach { setting ->
                    val settingId = setting.id
                    val onError: (Throwable?) -> Unit = { viewModel.onSettingError(settingId, it) }
                    val onValueChange: (Any?) -> Unit = { viewModel.onSettingEnter(settingId, it) }

                    with(setting) {
                        addContentItems(
                            navigation = navigation,
                            value = uiState.settings[settingId],
                            onError = onError,
                            onValueChange = onValueChange,
                        )
                    }
                }
            }
        }
    }
}
