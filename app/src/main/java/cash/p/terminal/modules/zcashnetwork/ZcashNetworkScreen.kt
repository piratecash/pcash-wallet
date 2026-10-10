package cash.p.terminal.modules.zcashnetwork

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.modules.blockchainstatus.BlockchainStatusButton
import cash.p.terminal.modules.btcblockchainsettings.BlockchainSettingCell
import cash.p.terminal.modules.evmnetwork.AddButton
import cash.p.terminal.modules.evmnetwork.CustomRpcListSection
import cash.p.terminal.modules.evmnetwork.RpcRowItem
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HeaderText
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.chartview.rememberAsyncImagePainterWithFallback
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.imageUrl

@Composable
internal fun ZcashNetworkScreen(
    uiState: ZcashNetworkViewModel.UiState,
    blockchain: Blockchain,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onAddServerClick: () -> Unit,
    onStatusClick: () -> Unit,
    onClose: () -> Unit,
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
) {
    var revealedCardId by remember { mutableStateOf<String?>(null) }

    Surface(color = ComposeAppTheme.colors.backgroundBase) {
        Column {
            ZcashNetworkAppBar(blockchain, onClose)

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(windowInsets),
            ) {
                item {
                    VSpacer(12.dp)
                    subhead2_grey(
                        modifier = Modifier.padding(horizontal = 32.dp),
                        text = stringResource(R.string.zcash_network_description)
                    )
                    VSpacer(32.dp)
                }

                item {
                    HeaderText(stringResource(R.string.zcash_network_default_servers))
                    CellUniversalLawrenceSection(uiState.defaultItems) { item ->
                        BlockchainSettingCell(item.name, item.url, item.selected, null) {
                            onSelect(item.url)
                        }
                    }
                }

                if (uiState.customItems.isNotEmpty()) {
                    CustomRpcListSection(
                        items = uiState.customItems,
                        toRow = { RpcRowItem(it.url, it.name, it.url, it.selected) },
                        revealedCardId = revealedCardId,
                        onClick = { onSelect(it.url) },
                        onReveal = { revealedCardId = it },
                        onConceal = { revealedCardId = null },
                        onDelete = { onDelete(it.url) },
                    )
                }

                item {
                    Spacer(Modifier.height(32.dp))
                    AddButton(onClick = onAddServerClick, titleRes = R.string.zcash_network_add_server)
                }

                item {
                    Spacer(Modifier.height(32.dp))
                    BlockchainStatusButton(onClick = onStatusClick)
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun ZcashNetworkAppBar(blockchain: Blockchain, onClose: () -> Unit) {
    AppBar(
        title = blockchain.name,
        navigationIcon = {
            Image(
                painter = rememberAsyncImagePainterWithFallback(
                    model = blockchain.type.imageUrl,
                    error = painterResource(R.drawable.ic_platform_placeholder_32)
                ),
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 14.dp)
                    .size(24.dp)
            )
        },
        menuItems = listOf(
            MenuItem(
                title = TranslatableString.ResString(R.string.Button_Close),
                icon = R.drawable.ic_close_24,
                onClick = onClose
            )
        )
    )
}

@Preview
@Composable
private fun ZcashNetworkScreenPreview() {
    ComposeAppTheme {
        ZcashNetworkScreen(
            uiState = ZcashNetworkViewModel.UiState(
                defaultItems = listOf(
                    ZcashNetworkViewModel.ViewItem("zec.rocks (global)", "https://zec.rocks:443", selected = true),
                    ZcashNetworkViewModel.ViewItem("zec.rocks (EU)", "https://eu.zec.rocks:443", selected = false),
                ),
                customItems = listOf(
                    ZcashNetworkViewModel.ViewItem("my.server.example:443", "https://my.server.example:443", false),
                ),
            ),
            blockchain = Blockchain(BlockchainType.Zcash, "Zcash", null),
            onSelect = {},
            onDelete = {},
            onAddServerClick = {},
            onStatusClick = {},
            onClose = {},
        )
    }
}
