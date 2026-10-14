package cash.p.terminal.modules.market.filters

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import cash.p.terminal.resources.Any
import cash.p.terminal.resources.Market_Filter_Blockchains
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_checkmark_20
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
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

class BlockchainsSelectorPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel = koinViewModel<MarketFiltersViewModel>(
            viewModelStoreOwner = navigation.viewModelStoreOwnerForPage(MarketFiltersPage::class)
        )
        NavigationBackHandler(
            state = rememberNavigationEventState(NavigationEventInfo.None),
            onBackCompleted = navigation::navigateUpSafely,
        )

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
                title = stringResource(Res.string.Market_Filter_Blockchains),
                navigationIcon = {
                    HsBackButton(onClick = { navigation.navigateUpSafely() })
                }
            )
        },
        containerColor = ComposeAppTheme.colors.backgroundBase,
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
            text = stringResource(Res.string.Any),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            painter = painterResource(Res.drawable.ic_checkmark_20),
            tint = ComposeAppTheme.colors.brandDefault,
            contentDescription = null,
            modifier = Modifier.alpha(if (checked) 1f else 0f)
        )
    }
}
