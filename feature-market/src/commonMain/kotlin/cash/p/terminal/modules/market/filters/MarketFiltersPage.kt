package cash.p.terminal.modules.market.filters

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberModalBottomSheetState
import cash.p.terminal.resources.Button_Reset
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Any
import cash.p.terminal.resources.Market_FilterSection_Indicators
import cash.p.terminal.resources.Market_FilterSection_MarketParameters
import cash.p.terminal.resources.Market_FilterSection_NetworkParameters
import cash.p.terminal.resources.Market_FilterSection_PriceParameters
import cash.p.terminal.resources.Market_Filter_Blockchains
import cash.p.terminal.resources.Market_Filter_ChooseSet
import cash.p.terminal.resources.Market_Filter_GoodDistribution
import cash.p.terminal.resources.Market_Filter_GoodDistribution_Description
import cash.p.terminal.resources.Market_Filter_ListedOnTopExchanges
import cash.p.terminal.resources.Market_Filter_MarketCap
import cash.p.terminal.resources.Market_Filter_OutperformedBnb
import cash.p.terminal.resources.Market_Filter_OutperformedBtc
import cash.p.terminal.resources.Market_Filter_OutperformedEth
import cash.p.terminal.resources.Market_Filter_PriceChange
import cash.p.terminal.resources.Market_Filter_PriceCloseToAth
import cash.p.terminal.resources.Market_Filter_PriceCloseToAtl
import cash.p.terminal.resources.Market_Filter_PricePeriod
import cash.p.terminal.resources.Market_Filter_SolidCex
import cash.p.terminal.resources.Market_Filter_SolidCex_Description
import cash.p.terminal.resources.Market_Filter_SolidDex
import cash.p.terminal.resources.Market_Filter_SolidDex_Description
import cash.p.terminal.resources.Market_Filter_TradingSignals
import cash.p.terminal.resources.Market_Filter_Volume
import cash.p.terminal.resources.Market_Filter_Volume24h
import cash.p.terminal.resources.Market_Filters
import cash.p.terminal.resources.ic_chart_24
import cash.p.terminal.resources.ic_checkmark_20
import cash.p.terminal.resources.ic_circle_clock_24
import cash.p.terminal.resources.ic_circle_coin_24
import cash.p.terminal.resources.ic_down_arrow_20
import cash.p.terminal.resources.ic_ring_24
import cash.p.terminal.resources.ic_usd_24
import cash.p.terminal.resources.icon_24_markets
import cash.p.terminal.ui_compose.TransparentModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.market.filtersresult.MarketFiltersResultsPage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.modules.market.filters.MarketFiltersModule.FilterDropdown.*
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellowWithSpinner
import cash.p.terminal.ui_compose.components.ButtonsGroupWithShade
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HeaderText
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.HsSwitch
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_grey
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.body_lucian
import cash.p.terminal.ui_compose.components.body_remus
import cash.p.terminal.ui_compose.components.CellUniversal
import cash.p.terminal.ui_compose.components.SectionUniversalLawrence
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.BottomSheetHeader
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.ui_compose.components.LocalHudMessenger
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import cash.p.terminal.modules.market.filters.PriceChange as FilterPriceChange

class MarketFiltersPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel = koinViewModel<MarketFiltersViewModel>()

        AdvancedSearchScreen(
            viewModel,
            navigation,
        )
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdvancedSearchScreen(
    viewModel: MarketFiltersViewModel,
    navigation: HSNavigation,
) {
    val uiState = viewModel.uiState
    val errorMessage = uiState.errorMessage
    val coroutineScope = rememberCoroutineScope()

    var bottomSheetType by remember { mutableStateOf<MarketFiltersModule.FilterDropdown?>(null) }
    val modalBottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    bottomSheetType?.let { type ->
        TransparentModalBottomSheet(
            onDismissRequest = { bottomSheetType = null },
            sheetState = modalBottomSheetState,
        ) {
            BottomSheetContent(
                bottomSheetType = type,
                viewModel = viewModel,
                onClose = {
                    coroutineScope.launch { modalBottomSheetState.hide() }
                        .invokeOnCompletion { bottomSheetType = null }
                }
            )
        }
    }

    Scaffold(
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = {
            AppBar(
                title = stringResource(Res.string.Market_Filters),
                navigationIcon = {
                    HsBackButton(onClick = { navigation.navigateUpSafely() })
                },
                menuItems = listOf(
                    MenuItem(
                        title = TranslatableString.ResString(Res.string.Button_Reset),
                        onClick = { viewModel.reset() }
                    )
                ),
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                AdvancedSearchContent(
                    viewModel = viewModel,
                    onFilterByBlockchainsClick = {
                        navigation.slideFromRight(BlockchainsSelectorPage())
                    },
                    showBottomSheet = { type -> bottomSheetType = type }
                )
            }

            ButtonsGroupWithShade {
                ButtonPrimaryYellowWithSpinner(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    title = uiState.buttonTitle,
                    onClick = {
                        navigation.slideFromRight(MarketFiltersResultsPage())
                    },
                    showSpinner = uiState.showSpinner,
                    enabled = uiState.buttonEnabled,
                )
            }
        }
    }

    errorMessage?.let {
        LocalHudMessenger.current.showError(it.getString())
    }
}

@Composable
private fun BottomSheetContent(
    bottomSheetType: MarketFiltersModule.FilterDropdown,
    viewModel: MarketFiltersViewModel,
    onClose: () -> Unit,
) {
    val uiState = viewModel.uiState
    when (bottomSheetType) {
        CoinSet -> {
            SingleSelectBottomSheetContent(
                title = Res.string.Market_Filter_ChooseSet,
                headerIcon = Res.drawable.ic_circle_coin_24,
                items = viewModel.coinListsViewItemOptions,
                selectedItem = uiState.coinListSet,
                onSelect = {
                    viewModel.updateCoinList(it)
                },
                onClose = onClose
            )
        }

        MarketCap -> {
            SingleSelectBottomSheetContent(
                title = Res.string.Market_Filter_MarketCap,
                headerIcon = Res.drawable.ic_usd_24,
                items = viewModel.marketCapViewItemOptions,
                selectedItem = uiState.marketCap,
                onSelect = {
                    viewModel.updateMarketCap(it)
                },
                onClose = onClose
            )
        }

        TradingVolume -> {
            SingleSelectBottomSheetContent(
                title = Res.string.Market_Filter_Volume24h,
                headerIcon = Res.drawable.ic_chart_24,
                items = viewModel.volumeViewItemOptions,
                selectedItem = uiState.volume,
                onSelect = {
                    viewModel.updateVolume(it)
                },
                onClose = onClose
            )
        }

        PriceChange -> {
            SingleSelectBottomSheetContent(
                title = Res.string.Market_Filter_PriceChange,
                headerIcon = Res.drawable.icon_24_markets,
                items = viewModel.priceChangeViewItemOptions,
                selectedItem = uiState.priceChange,
                onSelect = {
                    viewModel.updatePriceChange(it)
                },
                onClose = onClose
            )
        }

        PricePeriod -> {
            SingleSelectBottomSheetContent(
                title = Res.string.Market_Filter_PricePeriod,
                headerIcon = Res.drawable.ic_circle_clock_24,
                items = viewModel.periodViewItemOptions,
                selectedItem = uiState.period,
                onSelect = {
                    viewModel.updatePeriod(it)
                },
                onClose = onClose
            )
        }

        TradingSignals -> {
            SingleSelectBottomSheetContent(
                title = Res.string.Market_Filter_TradingSignals,
                headerIcon = Res.drawable.ic_ring_24,
                items = viewModel.tradingSignals,
                selectedItem = uiState.filterTradingSignal,
                onSelect = {
                    viewModel.updateTradingSignal(it)
                },
                onClose = onClose
            )
        }
    }
}

@Composable
fun AdvancedSearchContent(
    viewModel: MarketFiltersViewModel,
    onFilterByBlockchainsClick: () -> Unit,
    showBottomSheet: (MarketFiltersModule.FilterDropdown) -> Unit,
) {
    val uiState = viewModel.uiState

    VSpacer(height = 12.dp)

    SectionUniversalLawrence {
        AdvancedSearchDropdown(
            title = Res.string.Market_Filter_ChooseSet,
            value = uiState.coinListSet.title,
            borderTop = false,
            onDropdownClick = { showBottomSheet(CoinSet) }
        )
    }
    VSpacer(height = 32.dp)

    HeaderText(stringResource(Res.string.Market_FilterSection_MarketParameters))
    SectionUniversalLawrence {
        AdvancedSearchDropdown(
            title = Res.string.Market_Filter_MarketCap,
            value = uiState.marketCap.title,
            borderTop = false,
            onDropdownClick = { showBottomSheet(MarketCap) }
        )
        AdvancedSearchDropdown(
            title = Res.string.Market_Filter_Volume,
            value = uiState.volume.title,
            onDropdownClick = { showBottomSheet(TradingVolume) }
        )
        AdvancedSearchSwitch(
            title = Res.string.Market_Filter_ListedOnTopExchanges,
            enabled = uiState.listedOnTopExchangesOn,
            onChecked = { viewModel.updateListedOnTopExchangesOn(it) }
        )
        AdvancedSearchSwitch(
            title = Res.string.Market_Filter_SolidCex,
            subtitle = Res.string.Market_Filter_SolidCex_Description,
            enabled = uiState.solidCexOn,
            onChecked = { viewModel.updateSolidCexOn(it) }
        )
        AdvancedSearchSwitch(
            title = Res.string.Market_Filter_SolidDex,
            subtitle = Res.string.Market_Filter_SolidDex_Description,
            enabled = uiState.solidDexOn,
            onChecked = { viewModel.updateSolidDexOn(it) }
        )
        AdvancedSearchSwitch(
            title = Res.string.Market_Filter_GoodDistribution,
            subtitle = Res.string.Market_Filter_GoodDistribution_Description,
            enabled = uiState.goodDistributionOn,
            onChecked = { viewModel.updateGoodDistributionOn(it) }
        )
    }
    VSpacer(height = 32.dp)

    HeaderText(stringResource(Res.string.Market_FilterSection_PriceParameters))
    SectionUniversalLawrence {
        AdvancedSearchDropdown(
            title = Res.string.Market_Filter_PriceChange,
            value = uiState.priceChange.title,
            valueColor = uiState.priceChange.item?.color ?: TextColor.Grey,
            onDropdownClick = { showBottomSheet(PriceChange) }
        )
        AdvancedSearchDropdown(
            title = Res.string.Market_Filter_PricePeriod,
            value = uiState.period.title,
            onDropdownClick = { showBottomSheet(PricePeriod) }
        )
        AdvancedSearchSwitch(
            title = Res.string.Market_Filter_OutperformedBtc,
            enabled = uiState.outperformedBtcOn,
            onChecked = { viewModel.updateOutperformedBtcOn(it) }
        )
        AdvancedSearchSwitch(
            title = Res.string.Market_Filter_OutperformedEth,
            enabled = uiState.outperformedEthOn,
            onChecked = { viewModel.updateOutperformedEthOn(it) }
        )
        AdvancedSearchSwitch(
            title = Res.string.Market_Filter_OutperformedBnb,
            enabled = uiState.outperformedBnbOn,
            onChecked = { viewModel.updateOutperformedBnbOn(it) }
        )
        AdvancedSearchSwitch(
            title = Res.string.Market_Filter_PriceCloseToAth,
            enabled = uiState.priceCloseToAth,
            onChecked = { viewModel.updateOutperformedAthOn(it) }
        )
        AdvancedSearchSwitch(
            title = Res.string.Market_Filter_PriceCloseToAtl,
            enabled = uiState.priceCloseToAtl,
            onChecked = { viewModel.updateOutperformedAtlOn(it) }
        )
    }
    VSpacer(height = 32.dp)

    HeaderText(stringResource(Res.string.Market_FilterSection_NetworkParameters))
    SectionUniversalLawrence {
        AdvancedSearchDropdown(
            title = Res.string.Market_Filter_Blockchains,
            value = uiState.selectedBlockchainsValue,
            onDropdownClick = onFilterByBlockchainsClick
        )
    }
    VSpacer(height = 32.dp)

    HeaderText(stringResource(Res.string.Market_FilterSection_Indicators))
    SectionUniversalLawrence {
        AdvancedSearchDropdown(
            title = Res.string.Market_Filter_TradingSignals,
            value = uiState.filterTradingSignal.title,
            onDropdownClick = { showBottomSheet(TradingSignals) }
        )
    }
    VSpacer(32.dp)
}

@Composable
private fun AdvancedSearchDropdown(
    title: StringResource,
    value: String?,
    valueColor: TextColor = TextColor.Leah,
    borderTop: Boolean = true,
    onDropdownClick: () -> Unit,
) {
    CellUniversal(
        borderTop = borderTop,
        onClick = onDropdownClick
    ) {
        body_leah(
            text = stringResource(title),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.weight(1f))
        FilterMenu(value, valueColor) {
            onDropdownClick()
        }
    }
}

@Composable
private fun AdvancedSearchSwitch(
    title: StringResource,
    subtitle: StringResource? = null,
    enabled: Boolean,
    borderTop: Boolean = true,
    onChecked: (Boolean) -> Unit,
) {
    CellUniversal(
        borderTop = borderTop,
        onClick = { onChecked(!enabled) }
    ) {
        Column {
            body_leah(
                text = stringResource(title),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                VSpacer(height = 1.dp)
                subhead2_grey(
                    text = stringResource(subtitle),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        HsSwitch(
            checked = enabled,
            onCheckedChange = onChecked,
        )
    }
}

@Composable
private fun FilterMenu(title: String?, valueColor: TextColor, onClick: () -> Unit) {
    val valueText = title ?: stringResource(Res.string.Any)
    val textColor = if (title != null) valueColor else TextColor.Grey
    Row(
        Modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (textColor) {
            TextColor.Grey -> body_grey(
                text = valueText,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1
            )

            TextColor.Remus -> body_remus(
                text = valueText,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1
            )

            TextColor.Lucian -> body_lucian(
                text = valueText,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1
            )

            TextColor.Leah -> body_leah(
                text = valueText,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1
            )
        }
        Icon(
            modifier = Modifier.padding(start = 4.dp),
            painter = painterResource(Res.drawable.ic_down_arrow_20),
            contentDescription = null,
            tint = ComposeAppTheme.colors.iconSecondary
        )
    }
}

@Composable
private fun <ItemClass> SingleSelectBottomSheetContent(
    title: StringResource,
    headerIcon: DrawableResource,
    items: List<FilterViewItemWrapper<ItemClass>>,
    selectedItem: FilterViewItemWrapper<ItemClass>? = null,
    onSelect: (FilterViewItemWrapper<ItemClass>) -> Unit,
    onClose: (() -> Unit),
) {
    BottomSheetHeader(
        iconPainter = painterResource(headerIcon),
        title = stringResource(title),
        onCloseClick = onClose,
        iconTint = ColorFilter.tint(ComposeAppTheme.colors.brandDefault)
    ) {
        Spacer(Modifier.height(12.dp))
        CellUniversalLawrenceSection(
            items = items,
            showFrame = true
        ) { itemWrapper ->
            RowUniversal(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                onClick = {
                    onSelect(itemWrapper)
                    onClose()
                }
            ) {
                if (itemWrapper.title != null && itemWrapper.item is FilterPriceChange) {
                    when (itemWrapper.item.color) {
                        TextColor.Lucian -> body_lucian(text = itemWrapper.title)
                        TextColor.Remus -> body_remus(text = itemWrapper.title)
                        TextColor.Grey -> body_grey(text = itemWrapper.title)
                        TextColor.Leah -> body_leah(text = itemWrapper.title)
                    }
                } else {
                    if (itemWrapper.title != null) {
                        body_leah(text = itemWrapper.title)
                    } else {
                        body_grey(text = stringResource(Res.string.Any))
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                if (itemWrapper == selectedItem) {
                    Image(
                        modifier = Modifier.padding(start = 5.dp),
                        painter = painterResource(Res.drawable.ic_checkmark_20),
                        colorFilter = ColorFilter.tint(ComposeAppTheme.colors.brandDefault),
                        contentDescription = null
                    )
                }
            }
        }
        Spacer(Modifier.height(44.dp))
    }
}
