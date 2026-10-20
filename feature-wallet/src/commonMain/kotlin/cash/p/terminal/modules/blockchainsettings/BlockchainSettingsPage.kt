package cash.p.terminal.modules.blockchainsettings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.feature.wallet.WalletPlatformPages
import cash.p.terminal.modules.btcblockchainsettings.BtcBlockchainSettingsPage
import cash.p.terminal.modules.evmnetwork.EvmNetworkPage
import cash.p.terminal.modules.solananetwork.SolanaNetworkPage
import cash.p.terminal.modules.zcashnetwork.ZcashNetworkPage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.resources.BlockchainSettings_Title
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_arrow_right
import cash.p.terminal.resources.ic_platform_placeholder_32
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.chartview.rememberAsyncImagePainterWithFallback
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

class BlockchainSettingsPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        BlockchainSettingsScreen(
            navigation = navigation,
        )
    }

}

@Composable
private fun BlockchainSettingsScreen(
    navigation: HSNavigation,
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
    viewModel: BlockchainSettingsViewModel = koinViewModel(),
) {

    Surface(color = ComposeAppTheme.colors.backgroundBase) {
        Column {
            AppBar(
                title = stringResource(Res.string.BlockchainSettings_Title),
                navigationIcon = {
                    HsBackButton(onClick = { navigation.navigateUpSafely() })
                },
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(windowInsets)
                    .verticalScroll(rememberScrollState()),
            ) {
                Spacer(Modifier.height(12.dp))
                BlockchainSettingsBlock(
                    btcLikeChains = viewModel.btcLikeChains,
                    otherChains = viewModel.otherChains,
                    statusOnlyChains = viewModel.statusOnlyChains,
                    navigation = navigation
                )
                Spacer(Modifier.height(44.dp))
            }
        }
    }

}

@Composable
private fun BlockchainSettingsBlock(
    btcLikeChains: List<BlockchainSettingsModule.BlockchainViewItem>,
    otherChains: List<BlockchainSettingsModule.BlockchainViewItem>,
    statusOnlyChains: List<BlockchainSettingsModule.BlockchainViewItem>,
    navigation: HSNavigation
) {
    Column {
        CellUniversalLawrenceSection(btcLikeChains) { item ->
            BlockchainSettingCell(item) {
                onClick(item, navigation)
            }
        }
        Spacer(Modifier.height(32.dp))
        CellUniversalLawrenceSection(otherChains) { item ->
            BlockchainSettingCell(item) {
                onClick(item, navigation)
            }
        }
        if (statusOnlyChains.isNotEmpty()) {
            Spacer(Modifier.height(32.dp))
            CellUniversalLawrenceSection(statusOnlyChains) { item ->
                BlockchainSettingCell(item) {
                    onClick(item, navigation)
                }
            }
        }
    }
}

private fun onClick(
    item: BlockchainSettingsModule.BlockchainViewItem,
    navigation: HSNavigation
) {
    when (item.blockchainItem) {
        is BlockchainSettingsModule.BlockchainItem.Btc -> {
            navigation.slideFromBottom(BtcBlockchainSettingsPage(item.blockchainItem.blockchain))
        }

        is BlockchainSettingsModule.BlockchainItem.Evm -> {
            navigation.slideFromBottom(EvmNetworkPage(item.blockchainItem.blockchain))
        }

        is BlockchainSettingsModule.BlockchainItem.Solana -> {
            navigation.slideFromBottom(SolanaNetworkPage())
        }

        is BlockchainSettingsModule.BlockchainItem.Zcash -> {
            navigation.slideFromBottom(ZcashNetworkPage(item.blockchainItem.blockchain))
        }

        is BlockchainSettingsModule.BlockchainItem.StatusOnly -> {
            getKoinInstance<WalletPlatformPages>().blockchainStatus(item.blockchainItem.blockchain)
                ?.let(navigation::slideFromBottom)
        }
    }
}

@Composable
private fun BlockchainSettingCell(
    item: BlockchainSettingsModule.BlockchainViewItem,
    onClick: () -> Unit
) {
    RowUniversal(
        onClick = onClick
    ) {
        Image(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .size(32.dp),
            painter = rememberAsyncImagePainterWithFallback(
                model = item.imageUrl,
                error = painterResource(Res.drawable.ic_platform_placeholder_32)
            ),
            contentDescription = null,
        )
        Column(modifier = Modifier.weight(1f)) {
            body_leah(text = item.title)
            subhead2_grey(text = item.subtitle)
        }
        Icon(
            modifier = Modifier.padding(horizontal = 16.dp),
            painter = painterResource(Res.drawable.ic_arrow_right),
            contentDescription = null,
            tint = ComposeAppTheme.colors.iconSecondary
        )
    }
}
