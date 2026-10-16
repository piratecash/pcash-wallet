package cash.p.terminal.modules.solananetwork

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.blockchainstatus.BlockchainStatusButton
import cash.p.terminal.modules.blockchainstatus.blockchainStatusOpener
import cash.p.terminal.modules.btcblockchainsettings.BlockchainSettingsAppBar
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpFrom
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.resources.BtcBlockchainSettings_RestoreSourceSettingsDescription
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_checkmark_20
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.MarketKitWrapper
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.imageUrl
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

class SolanaNetworkPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        SolanaNetworkScreen(
            page = this,
            viewModel = koinViewModel(),
            navigation = navigation,
        )
    }

}

@Composable
private fun SolanaNetworkScreen(
    page: HSPage,
    viewModel: SolanaNetworkViewModel,
    navigation: HSNavigation,
) {

    if (viewModel.closeScreen) {
        navigation.navigateUpFrom(page)
    }

    Surface(color = ComposeAppTheme.colors.backgroundBase) {
        Column {
            BlockchainSettingsAppBar(viewModel.title, viewModel.blockchainType.imageUrl, navigation::navigateUpSafely)

            SolanaNetworkList(viewModel.viewItems, viewModel::onSelectViewItem, navigation)
        }

    }
}

@Composable
private fun SolanaNetworkList(
    viewItems: List<SolanaNetworkViewModel.ViewItem>,
    onSelect: (SolanaNetworkViewModel.ViewItem) -> Unit,
    navigation: HSNavigation,
) {
    val openStatus = blockchainStatusOpener(navigation)
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
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
            CellUniversalLawrenceSection(viewItems) { item ->
                NetworkSettingCell(item.name, item.url, item.selected) {
                    onSelect(item)
                }
            }
            Spacer(Modifier.height(32.dp))
        }

        if (openStatus != null) {
            item {
                val marketKit = koinInject<MarketKitWrapper>()
                BlockchainStatusButton(onClick = {
                    marketKit.blockchain(BlockchainType.Solana.uid)?.let(openStatus)
                })
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun NetworkSettingCell(
    title: String,
    subtitle: String,
    checked: Boolean,
    onClick: () -> Unit
) {
    RowUniversal(
        onClick = onClick
    ) {
        Column(modifier = Modifier
            .padding(start = 16.dp)
            .weight(1f)) {
            body_leah(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(1.dp))
            subhead2_grey(
                text = subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .width(52.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(
                    painter = painterResource(Res.drawable.ic_checkmark_20),
                    tint = ComposeAppTheme.colors.brandDefault,
                    contentDescription = null,
                )
            }
        }
    }
}
