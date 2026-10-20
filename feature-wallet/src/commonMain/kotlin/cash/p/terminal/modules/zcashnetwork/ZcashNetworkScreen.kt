package cash.p.terminal.modules.zcashnetwork

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.blockchainstatus.BlockchainStatusButton
import cash.p.terminal.modules.btcblockchainsettings.BlockchainSettingCell
import cash.p.terminal.modules.btcblockchainsettings.BlockchainSettingsAppBar
import cash.p.terminal.modules.evmnetwork.AddButton
import cash.p.terminal.modules.evmnetwork.CustomRpcListSection
import cash.p.terminal.modules.evmnetwork.RpcRowItem
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.zcash_network_add_server
import cash.p.terminal.resources.zcash_network_auto
import cash.p.terminal.resources.zcash_network_auto_current
import cash.p.terminal.resources.zcash_network_auto_hint
import cash.p.terminal.resources.zcash_network_default_servers
import cash.p.terminal.resources.zcash_network_description
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HeaderText
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.imageUrl
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ZcashNetworkScreen(
    uiState: ZcashNetworkViewModel.UiState,
    blockchain: Blockchain,
    serverActions: ZcashServerListActions,
    onAddServerClick: () -> Unit,
    onStatusClick: (() -> Unit)?,
    onClose: () -> Unit,
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
) {
    var revealedCardId by remember { mutableStateOf<String?>(null) }

    Surface(color = ComposeAppTheme.colors.backgroundBase) {
        Column {
            BlockchainSettingsAppBar(blockchain.name, blockchain.type.imageUrl, onClose)

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(windowInsets),
            ) {
                item {
                    VSpacer(12.dp)
                    subhead2_grey(
                        modifier = Modifier.padding(horizontal = 32.dp),
                        text = stringResource(Res.string.zcash_network_description)
                    )
                    VSpacer(32.dp)
                }

                serverSections(uiState, serverActions, revealedCardId) { revealedCardId = it }

                item {
                    Spacer(Modifier.height(32.dp))
                    AddButton(onClick = onAddServerClick, titleRes = Res.string.zcash_network_add_server)
                }

                if (onStatusClick != null) {
                    item {
                        Spacer(Modifier.height(32.dp))
                        BlockchainStatusButton(onClick = onStatusClick)
                        Spacer(Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}

internal class ZcashServerListActions(
    val onSelectAuto: () -> Unit,
    val onSelect: (String) -> Unit,
    val onDelete: (String) -> Unit,
)

private fun LazyListScope.serverSections(
    uiState: ZcashNetworkViewModel.UiState,
    serverActions: ZcashServerListActions,
    revealedCardId: String?,
    onRevealedChange: (String?) -> Unit,
) {
        item {
            CellUniversalLawrenceSection {
                BlockchainSettingCell(
                    stringResource(Res.string.zcash_network_auto),
                    uiState.autoServerName?.let { stringResource(Res.string.zcash_network_auto_current, it) }
                        ?: stringResource(Res.string.zcash_network_auto_hint),
                    uiState.isAuto,
                    null,
                    serverActions.onSelectAuto,
                )
            }
            VSpacer(32.dp)
        }

        item {
            HeaderText(stringResource(Res.string.zcash_network_default_servers))
            CellUniversalLawrenceSection(uiState.defaultItems) { item ->
                BlockchainSettingCell(item.name, item.url, item.selected, null) {
                    serverActions.onSelect(item.url)
                }
            }
        }

        if (uiState.customItems.isNotEmpty()) {
            CustomRpcListSection(
                items = uiState.customItems,
                toRow = { RpcRowItem(it.url, it.name, it.url, it.selected) },
                revealedCardId = revealedCardId,
                onClick = { serverActions.onSelect(it.url) },
                onReveal = onRevealedChange,
                onConceal = { onRevealedChange(null) },
                onDelete = { serverActions.onDelete(it.url) },
            )
        }
}
