package cash.p.terminal.modules.evmnetwork

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.blockchainstatus.BlockchainStatusButton
import cash.p.terminal.modules.blockchainstatus.blockchainStatusOpener
import cash.p.terminal.modules.btcblockchainsettings.BlockchainSettingCell
import cash.p.terminal.modules.btcblockchainsettings.BlockchainSettingsAppBar
import cash.p.terminal.modules.evmnetwork.addrpc.AddRpcPage
import cash.p.terminal.modules.walletconnect.list.ui.ActionsRow
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.resources.BtcBlockchainSettings_RestoreSourceSettingsDescription
import cash.p.terminal.resources.EvmNetwork_AddNew
import cash.p.terminal.resources.EvmNetwork_Added
import cash.p.terminal.resources.Hud_Removed
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.WalletConnect_Unnamed
import cash.p.terminal.resources.ic_checkmark_20
import cash.p.terminal.resources.ic_circle_minus_24
import cash.p.terminal.resources.ic_plus
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.DraggableCardSimple
import cash.p.terminal.ui_compose.components.HeaderText
import cash.p.terminal.ui_compose.components.HsIconButton
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_brand
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.getShape
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import cash.p.terminal.ui_compose.components.showDivider
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.imageUrl
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class EvmNetworkPage(val input: Blockchain) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        EvmNetworkScreen(
            navigation = navigation,
            blockchain = input,
            onBackPress = navigation::navigateUpSafely,
        )
    }

}

@Composable
private fun EvmNetworkScreen(
    navigation: HSNavigation,
    blockchain: Blockchain,
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
    onBackPress: () -> Unit,
) {
    val viewModel: EvmNetworkViewModel = koinViewModel { parametersOf(blockchain) }
    var revealedCardId by remember { mutableStateOf<String?>(null) }
    val hudMessenger = rememberHudMessenger()
    val removedText = stringResource(Res.string.Hud_Removed)
    val openStatus = blockchainStatusOpener(navigation)

    Surface(color = ComposeAppTheme.colors.backgroundBase) {
        Column {
            BlockchainSettingsAppBar(viewModel.title, viewModel.blockchain.type.imageUrl, onBackPress)

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(windowInsets),
            ) {

                item {
                    VSpacer(12.dp)
                    subhead2_grey(
                        modifier = Modifier.padding(horizontal = 32.dp),
                        text = stringResource(Res.string.BtcBlockchainSettings_RestoreSourceSettingsDescription)
                    )
                    VSpacer(32.dp)
                }

                item {
                    CellUniversalLawrenceSection(viewModel.viewState.defaultItems) { item ->
                        BlockchainSettingCell(item.name, item.url, item.selected, null) {
                            viewModel.onSelectSyncSource(item.syncSource)
                        }
                    }
                }

                if (viewModel.viewState.customItems.isNotEmpty()) {
                    CustomRpcListSection(
                        viewModel.viewState.customItems,
                        EvmNetworkViewModel.ViewItem::toRpcRow,
                        revealedCardId,
                        onClick = { viewModel.onSelectSyncSource(it.syncSource) },
                        onReveal = { revealedCardId = it },
                        onConceal = { revealedCardId = null }
                    ) {
                        viewModel.onRemoveCustomRpc(it.syncSource)
                        hudMessenger.showError(removedText)
                    }
                }

                item {
                    Spacer(Modifier.height(32.dp))
                    AddButton(onClick = { navigation.slideFromBottom(AddRpcPage(blockchain)) })
                }

                if (openStatus != null) {
                    item {
                        Spacer(Modifier.height(32.dp))
                        BlockchainStatusButton(onClick = { openStatus(blockchain) })
                        Spacer(Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}

internal data class RpcRowItem(val id: String, val name: String, val url: String, val selected: Boolean)

private fun EvmNetworkViewModel.ViewItem.toRpcRow() = RpcRowItem(id, name, url, selected)

internal fun <T> LazyListScope.CustomRpcListSection(
    items: List<T>,
    toRow: (T) -> RpcRowItem,
    revealedCardId: String?,
    onClick: (T) -> Unit,
    onReveal: (String) -> Unit,
    onConceal: () -> Unit,
    onDelete: (T) -> Unit
) {
    item {
        Spacer(Modifier.height(32.dp))
        HeaderText(
            stringResource(Res.string.EvmNetwork_Added),
        )
    }
    itemsIndexed(items, key = { _, item -> toRow(item).id }) { index, item ->
        val row = toRow(item)
        val showDivider = showDivider(items.size, index)
        val shape = getShape(items.size, index)
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            ActionsRow(
                content = {
                    HsIconButton(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(88.dp),
                        onClick = { onDelete(item) },
                        content = {
                            Icon(
                                painter = painterResource(Res.drawable.ic_circle_minus_24),
                                tint = Color.Gray,
                                contentDescription = "delete",
                            )
                        }
                    )
                },
            )
            DraggableCardSimple(
                key = row.id,
                isRevealed = revealedCardId == row.id,
                cardOffset = 72f,
                onReveal = { onReveal(row.id) },
                onConceal = onConceal,
                content = {
                    RpcCell(
                        shape = shape,
                        showDivider = showDivider,
                        item = row,
                        onItemClick = { onClick(item) }
                    )
                }
            )
        }
    }
}

@Composable
internal fun AddButton(
    onClick: () -> Unit,
    titleRes: StringResource = Res.string.EvmNetwork_AddNew,
) {
    CellUniversalLawrenceSection(
        listOf {
            RowUniversal(
                onClick = onClick,
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_plus),
                    modifier = Modifier.size(24.dp),
                    tint = ComposeAppTheme.colors.brandDefault,
                    contentDescription = null
                )
                Spacer(Modifier.width(16.dp))
                body_brand(
                    text = stringResource(titleRes)
                )
            }
        }
    )
}

@Composable
internal fun RpcCell(
    shape: Shape,
    item: RpcRowItem,
    showDivider: Boolean = false,
    onItemClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(shape)
            .background(ComposeAppTheme.colors.surfacePrimary)
            .clickable {
                onItemClick()
            },
        contentAlignment = Alignment.Center
    ) {
        if (showDivider) {
            HorizontalDivider(
                thickness = 1.dp,
                color = ComposeAppTheme.colors.borderDivider,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
        Row(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val title = when {
                    item.name.isNotBlank() -> item.name
                    else -> stringResource(Res.string.WalletConnect_Unnamed)
                }

                body_leah(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                subhead2_grey(text = item.url)
            }
            if (item.selected) {
                Icon(
                    painter = painterResource(Res.drawable.ic_checkmark_20),
                    tint = ComposeAppTheme.colors.brandDefault,
                    contentDescription = null
                )
            }
        }
    }
}
