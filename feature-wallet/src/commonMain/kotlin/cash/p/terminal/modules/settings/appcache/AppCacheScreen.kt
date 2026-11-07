package cash.p.terminal.modules.settings.appcache

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.settings_app_cache_clear
import cash.p.terminal.resources.settings_app_cache_clear_all
import cash.p.terminal.resources.settings_app_cache_cleared
import cash.p.terminal.resources.settings_app_cache_description
import cash.p.terminal.resources.settings_app_cache_title
import cash.p.terminal.modules.settings.appcache.AppCacheModule.CacheItemViewItem
import cash.p.terminal.modules.settings.appcache.AppCacheModule.CacheType
import cash.p.terminal.modules.settings.appcache.AppCacheModule.UiState
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryRed
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.InfoText
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AppCacheScreen(navigation: HSNavigation) {
    val viewModel = koinViewModel<AppCacheViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val hudMessenger = rememberHudMessenger()
    val cleared = stringResource(Res.string.settings_app_cache_cleared)

    AppCacheContent(
        uiState = uiState,
        onBackClick = navigation::navigateUpSafely,
        onClearAllClick = {
            viewModel.clearAllCaches()
            hudMessenger.showSuccess(cleared)
        },
        onClearItemClick = { type ->
            viewModel.clearCache(type)
            hudMessenger.showSuccess(cleared)
        }
    )
}

@Composable
private fun AppCacheContent(
    uiState: UiState,
    onBackClick: () -> Unit,
    onClearAllClick: () -> Unit,
    onClearItemClick: (CacheType) -> Unit
) {
    Scaffold(
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = {
            AppBar(
                title = stringResource(Res.string.settings_app_cache_title),
                navigationIcon = {
                    HsBackButton(onClick = onBackClick)
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            VSpacer(12.dp)

            ButtonPrimaryRed(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                title = stringResource(Res.string.settings_app_cache_clear_all),
                onClick = onClearAllClick,
                enabled = !uiState.isClearing
            )

            VSpacer(24.dp)

            CellUniversalLawrenceSection(uiState.cacheItems) { item ->
                CacheItemRow(
                    item = item,
                    isClearing = uiState.clearingItemId == item.type,
                    onClearClick = { onClearItemClick(item.type) }
                )
            }

            VSpacer(12.dp)

            InfoText(text = stringResource(Res.string.settings_app_cache_description))

            VSpacer(32.dp)
        }
    }
}

@Composable
private fun CacheItemRow(
    item: CacheItemViewItem,
    isClearing: Boolean,
    onClearClick: () -> Unit
) {
    RowUniversal(
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            body_leah(text = stringResource(item.title))
            VSpacer(1.dp)
            subhead2_grey(text = stringResource(item.description))
        }

        HSpacer(16.dp)

        if (isClearing) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = ComposeAppTheme.colors.textSecondary,
                strokeWidth = 2.dp
            )
        } else {
            ButtonPrimaryYellow(
                title = stringResource(Res.string.settings_app_cache_clear),
                onClick = onClearClick
            )
        }
    }
}
