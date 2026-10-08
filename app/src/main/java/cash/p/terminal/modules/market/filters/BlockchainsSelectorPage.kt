package cash.p.terminal.modules.market.filters

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cash.p.terminal.R
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.viewModelStoreOwnerForPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_grey
import io.horizontalsystems.chartview.cell.CellBlockchainChecked
import cash.p.terminal.ui_compose.components.CellUniversal
import cash.p.terminal.ui_compose.components.SectionUniversalLawrence
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

class BlockchainsSelectorPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel = viewModel<MarketFiltersViewModel>(
            viewModelStoreOwner = navigation.viewModelStoreOwnerForPage(MarketFiltersPage::class),
            factory = MarketFiltersModule.Factory()
        )
        BackHandler { navigation.navigateUpSafely() }

        FilterByBlockchainsScreen(
            viewModel,
            navigation,
        )
    }
}

@Composable
private fun FilterByBlockchainsScreen(
    viewModel: MarketFiltersViewModel,
    navigation: HSNavigation
) {
    val uiState = viewModel.uiState

    Scaffold(
        topBar = {
            AppBar(
                title = stringResource(R.string.Market_Filter_Blockchains),
                navigationIcon = {
                    HsBackButton(onClick = { navigation.navigateUpSafely() })
                }
            )
        },
        containerColor = ComposeAppTheme.colors.tyler,
    ) {
        Column(
            modifier = Modifier
                .padding(it)
                .verticalScroll(rememberScrollState())
        ) {
            VSpacer(height = 12.dp)

            SectionUniversalLawrence {
                AnyCell(
                    checked = uiState.selectedBlockchains.isEmpty(),
                    onClick = { viewModel.anyBlockchains() }
                )
                uiState.blockchainOptions.forEach { item ->
                    CellBlockchainChecked(
                        blockchain = item.blockchain,
                        checked = item.checked
                    ) {
                        if (item.checked) {
                            viewModel.onBlockchainUncheck(item.blockchain)
                        } else {
                            viewModel.onBlockchainCheck(item.blockchain)
                        }
                    }
                }
            }

            VSpacer(height = 32.dp)
        }
    }
}

@Composable
private fun AnyCell(
    checked: Boolean,
    onClick: () -> Unit
) {
    CellUniversal(
        borderTop = false,
        onClick = onClick
    ) {
        body_grey(
            modifier = Modifier
                .padding(end = 16.dp)
                .weight(1f),
            text = stringResource(R.string.Any),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            painter = painterResource(R.drawable.ic_checkmark_20),
            tint = ComposeAppTheme.colors.jacob,
            contentDescription = null,
            modifier = Modifier.alpha(if (checked) 1f else 0f)
        )
    }
}
