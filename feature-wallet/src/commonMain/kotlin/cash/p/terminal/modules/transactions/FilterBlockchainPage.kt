package cash.p.terminal.modules.transactions

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cash.p.terminal.feature.wallet.WalletPlatformPages
import org.koin.compose.koinInject
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.navigation.viewModelStoreOwnerForPage
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.CellMultilineClear
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.chartview.rememberAsyncImagePainterWithFallback
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.imageUrl
import org.koin.compose.viewmodel.koinViewModel
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Transactions_Filter_AllBlockchains
import cash.p.terminal.resources.Transactions_Filter_ChooseBlockchain
import cash.p.terminal.resources.ic_platform_placeholder_32
import cash.p.terminal.resources.icon_20_check_1
import cash.p.terminal.resources.icon_24_circle_coin

class FilterBlockchainPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel: TransactionsViewModel = koinViewModel(
            viewModelStoreOwner = navigation.viewModelStoreOwnerForPage(koinInject<WalletPlatformPages>().mainPage)
        )

        FilterBlockchainScreen(navigation, viewModel)
    }
}


@Composable
fun FilterBlockchainScreen(navigation: HSNavigation, viewModel: TransactionsViewModel) {
    val filterBlockchains by viewModel.filterBlockchainsFlow.collectAsStateWithLifecycle()

    Surface(color = ComposeAppTheme.colors.backgroundBase) {
        Column {
            AppBar(
                title = stringResource(Res.string.Transactions_Filter_ChooseBlockchain),
                navigationIcon = {
                    HsBackButton(onClick = navigation::navigateUpSafely)
                }
            )
            filterBlockchains?.let { blockchains ->
                LazyColumn(
                    modifier = Modifier.navigationBarsPadding(),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    items(blockchains) { filterItem ->
                        BlockchainCell(viewModel, filterItem, navigation)
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockchainCell(
    viewModel: TransactionsViewModel,
    filterItem: Filter<Blockchain?>,
    navigation: HSNavigation
) {
    CellMultilineClear(borderTop = true) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .clickable {
                    viewModel.onEnterFilterBlockchain(filterItem)
                    navigation.navigateUpSafely()
                }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val blockchain = filterItem.item
            if (blockchain != null) {
                Image(
                    painter = rememberAsyncImagePainterWithFallback(
                        model = blockchain.type.imageUrl,
                        error = painterResource(Res.drawable.ic_platform_placeholder_32)
                    ),
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .size(32.dp),
                    contentDescription = null
                )
                body_leah(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .weight(1f),
                    text = blockchain.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Image(
                    painter = painterResource(Res.drawable.icon_24_circle_coin),
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .size(24.dp),
                    contentDescription = null
                )
                body_leah(text = stringResource(Res.string.Transactions_Filter_AllBlockchains))
            }
            if (filterItem.selected) {
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    painter = painterResource(Res.drawable.icon_20_check_1),
                    contentDescription = null,
                    tint = ComposeAppTheme.colors.brandDefault
                )
            }
        }
    }
}
