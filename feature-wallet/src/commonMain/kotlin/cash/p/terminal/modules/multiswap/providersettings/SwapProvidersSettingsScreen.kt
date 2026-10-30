package cash.p.terminal.modules.multiswap.providersettings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cash.p.terminal.resources.Res
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.HsDivider
import cash.p.terminal.ui_compose.components.HsImage
import cash.p.terminal.ui_compose.components.HsSwitch
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.SectionUniversalLawrence
import cash.p.terminal.ui_compose.components.body_disabled
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.resources.swap_providers_mandatory_toast
import cash.p.terminal.resources.swap_providers_title
import cash.p.terminal.ui_compose.components.rememberHudMessenger

@Composable
internal fun SwapProvidersSettingsScreen(
    uiState: SwapProvidersSettingsUiState,
    onToggle: (String, Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val hud = rememberHudMessenger()
    val mandatoryToast = stringResource(Res.string.swap_providers_mandatory_toast)

    Scaffold(
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = {
            AppBar(
                title = stringResource(Res.string.swap_providers_title),
                navigationIcon = { HsBackButton(onClick = onClose) },
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp)
        ) {
            SectionUniversalLawrence {
                Column {
                    uiState.items.forEachIndexed { index, item ->
                        if (index > 0) {
                            HsDivider()
                        }
                        ProviderRow(
                            item = item,
                            onToggle = { enabled ->
                                if (item.mandatory) {
                                    hud.showError(mandatoryToast)
                                } else {
                                    onToggle(item.id, enabled)
                                }
                            },
                            onMandatoryClick = {
                                hud.showError(mandatoryToast)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderRow(
    item: SwapProviderItem,
    onToggle: (Boolean) -> Unit,
    onMandatoryClick: () -> Unit,
) {
    RowUniversal(
        modifier = Modifier.padding(horizontal = 16.dp),
        onClick = if (item.mandatory) onMandatoryClick else null,
    ) {
        HsImage(
            url = item.iconUrl,
            placeholder = item.icon,
            modifier = Modifier.size(32.dp),
        )
        HSpacer(16.dp)
        if (item.enabled) {
            body_leah(
                text = item.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        } else {
            body_disabled(
                text = item.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        HsSwitch(
            checked = item.enabled,
            enabled = !item.mandatory,
            onCheckedChange = onToggle,
        )
    }
}
