package cash.p.terminal.modules.coin.majorholders

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cash.p.terminal.resources.CoinPage_MajorHolders_HoldersNumber
import cash.p.terminal.resources.CoinPage_MajorHolders_InTopWallets
import cash.p.terminal.resources.Hud_Text_Copied
import cash.p.terminal.resources.Market_SeeAll
import cash.p.terminal.resources.SyncError
import cash.p.terminal.resources.ic_arrow_right
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.resources.Button_Close
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_close_24
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.modules.coin.MajorHolderItem
import cash.p.terminal.modules.coin.overview.ui.Loading
import cash.p.terminal.strings.helpers.shorten
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonSecondaryDefault
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui.compose.components.ListErrorView
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.SectionItemBorderedRowUniversalClear
import cash.p.terminal.ui.compose.components.SnackbarError
import cash.p.terminal.ui.compose.components.StackedBarChart
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.captionSB_grey
import cash.p.terminal.ui_compose.components.headline1_bran
import cash.p.terminal.ui_compose.components.subhead1_grey
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.core.entities.Blockchain
import cash.p.terminal.core.IClipboardManager
import cash.p.terminal.ui_compose.components.LocalHudMessenger
import cash.p.terminal.ui_compose.components.LocalUrlOpener
import io.horizontalsystems.core.common.CommonParcelable
import io.horizontalsystems.core.common.CommonParcelize
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class CoinMajorHoldersPage(val input: Input) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        CoinMajorHoldersScreen(
            input.coinUid,
            input.blockchain,
            navigation,
        )
    }

    @CommonParcelize
    data class Input(val coinUid: String, val blockchain: Blockchain) : CommonParcelable
}

@Composable
private fun CoinMajorHoldersScreen(
    coinUid: String,
    blockchain: Blockchain,
    navigation: HSNavigation,
    viewModel: CoinMajorHoldersViewModel = koinViewModel { parametersOf(coinUid, blockchain) }
) {

    Surface(color = ComposeAppTheme.colors.backgroundBase) {
        Column {
            AppBar(
                title = blockchain.name,
                menuItems = listOf(
                    MenuItem(
                        title = TranslatableString.ResString(Res.string.Button_Close),
                        icon = Res.drawable.ic_close_24,
                        onClick = { navigation.navigateUpSafely() }
                    )
                )
            )

            Crossfade(viewModel.uiState.viewState) { viewState ->
                when (viewState) {
                    ViewState.Loading -> {
                        Loading()
                    }

                    is ViewState.Error -> {
                        ListErrorView(stringResource(Res.string.SyncError), viewModel::onErrorClick)
                        viewModel.uiState.error?.let {
                            SnackbarError(it.getString())
                            viewModel.errorShown()
                        }
                    }

                    ViewState.Success -> {
                        CoinMajorHoldersContent(viewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun CoinMajorHoldersContent(
    viewModel: CoinMajorHoldersViewModel,
) {
    val uiState = viewModel.uiState

    val urlOpener = LocalUrlOpener.current

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(bottom = 30.dp)
    ) {

        item {
            HoldersGeneralInfo(uiState.top10Share, uiState.totalHoldersCount)
        }

        item {
            StackedBarChart(
                slices = uiState.chartData,
                modifier = Modifier.padding(top = 12.dp, start = 16.dp, end = 16.dp, bottom = 24.dp)
            )
        }

        items(uiState.topHolders) {
            TopWalletCell(it)
        }

        item {
            uiState.seeAllUrl?.let {
                SeeAllButton { urlOpener.open(it) }
            }
        }
    }
}

@Composable
private fun HoldersGeneralInfo(top10Share: String, totalHoldersCount: String) {
    VSpacer(12.dp)
    subhead2_grey(
        modifier = Modifier.padding(horizontal = 16.dp),
        text = TranslatableString.ResString(
            Res.string.CoinPage_MajorHolders_HoldersNumber,
            totalHoldersCount
        ).getString()
    )
    VSpacer(12.dp)
    Row(
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        headline1_bran(
            text = top10Share,
            modifier = Modifier.alignByBaseline()
        )
        HSpacer(8.dp)
        subhead1_grey(
            text = stringResource(Res.string.CoinPage_MajorHolders_InTopWallets),
            modifier = Modifier.alignByBaseline()
        )
    }
}

@Composable
private fun SeeAllButton(onClick: () -> Unit) {
    VSpacer(32.dp)
    CellUniversalLawrenceSection(
        listOf {
            RowUniversal(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                onClick = onClick
            ) {
                body_leah(
                    text = stringResource(Res.string.Market_SeeAll),
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    painter = painterResource(Res.drawable.ic_arrow_right),
                    contentDescription = null,
                    tint = ComposeAppTheme.colors.iconSecondary
                )
            }
        }
    )
    VSpacer(32.dp)
}

@Composable
private fun TopWalletCell(item: MajorHolderItem) {
    val clipboardManager = koinInject<IClipboardManager>()
    val hudMessenger = LocalHudMessenger.current
    val copiedText = stringResource(Res.string.Hud_Text_Copied)

    SectionItemBorderedRowUniversalClear(borderTop = true) {
        captionSB_grey(
            text = item.index.toString(),
            modifier = Modifier.width(24.dp),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            body_leah(text = item.sharePercent)
            VSpacer(1.dp)
            subhead2_grey(text = item.balance)
        }

        ButtonSecondaryDefault(
            modifier = Modifier
                .padding(start = 8.dp, end = 8.dp)
                .height(28.dp),
            title = item.address.shorten(),
            onClick = {
                clipboardManager.copyText(item.address)
                hudMessenger.showSuccess(copiedText)
            }
        )
    }
}
