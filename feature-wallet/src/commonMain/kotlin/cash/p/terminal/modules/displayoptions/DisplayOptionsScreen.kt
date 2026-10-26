package cash.p.terminal.modules.displayoptions

import org.jetbrains.compose.resources.painterResource
import cash.p.terminal.resources.ic_arrow_right
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.managewallets.ManageWalletsPage
import cash.p.terminal.ui_compose.components.HsSettingCell
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.resources.ManageCoins_title
import cash.p.terminal.resources.Market_FilterSection_PriceParameters
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.display_options
import cash.p.terminal.resources.display_options_percent_change
import cash.p.terminal.resources.display_options_price_change
import cash.p.terminal.resources.display_options_price_period
import cash.p.terminal.resources.sum_rounding
import cash.p.terminal.ui_compose.Select
import cash.p.terminal.ui_compose.components.AlertGroup
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.InfoText
import cash.p.terminal.ui_compose.components.SwitchWithText
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun DisplayOptionsScreen(
    navigation: HSNavigation,
    uiState: DisplayOptionsUiState,
    onPricePeriodChanged: (DisplayPricePeriod) -> Unit,
    onPercentChangeToggled: (Boolean) -> Unit,
    onPriceChangeToggled: (Boolean) -> Unit,
    onRoundingAmountMainPageToggled: (Boolean) -> Unit,
) {
    var showPeriodSelector by remember { mutableStateOf(false) }

    Surface(color = ComposeAppTheme.colors.backgroundBase) {
        Column {
            AppBar(
                title = stringResource(Res.string.display_options),
                navigationIcon = {
                    HsBackButton(onClick = navigation::navigateUpSafely)
                }
            )

            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
            ) {
                if (uiState.isCoinManagerEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    CellUniversalLawrenceSection(
                        listOf {
                            HsSettingCell(
                                title = stringResource(Res.string.ManageCoins_title),
                                arrowPainter = painterResource(Res.drawable.ic_arrow_right),
                                onClick = {
                                    navigation.slideFromRight(ManageWalletsPage())
                                }
                            )
                        }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                PriceParametersSection(
                    pricePeriod = uiState.pricePeriod,
                    displayDiffOptionType = uiState.displayDiffOptionType,
                    isRoundingAmount = uiState.isRoundingAmountMainPage,
                    onPricePeriodClick = { showPeriodSelector = true },
                    onPercentChangeToggled = onPercentChangeToggled,
                    onPriceChangeToggled = onPriceChangeToggled,
                    onRoundingAmountToggled = onRoundingAmountMainPageToggled,
                )
            }
        }
    }

    // Period Selector Dialog
    if (showPeriodSelector) {
        AlertGroup(
            title = Res.string.display_options_price_period,
            select = Select(uiState.pricePeriod, DisplayPricePeriod.entries),
            onSelect = { selected ->
                onPricePeriodChanged(selected)
                showPeriodSelector = false
            },
            onDismiss = { showPeriodSelector = false }
        )
    }
}

@Composable
fun PriceParametersSection(
    pricePeriod: DisplayPricePeriod,
    displayDiffOptionType: DisplayDiffOptionType,
    isRoundingAmount: Boolean,
    onPricePeriodClick: () -> Unit,
    onPercentChangeToggled: (Boolean) -> Unit,
    onPriceChangeToggled: (Boolean) -> Unit,
    onRoundingAmountToggled: (Boolean) -> Unit,
) {
    InfoText(
        text = stringResource(Res.string.Market_FilterSection_PriceParameters).uppercase(),
        paddingBottom = 8.dp
    )

    CellUniversalLawrenceSection(
        listOf(
            {
                HsSettingCell(
                    title = stringResource(Res.string.display_options_price_period),
                    arrowPainter = painterResource(Res.drawable.ic_arrow_right),
                    value = pricePeriod.shortForm.getString(),
                    onClick = onPricePeriodClick
                )
            },
            {
                SwitchWithText(
                    text = stringResource(Res.string.display_options_percent_change),
                    checked = displayDiffOptionType.hasPercentChange,
                    onCheckedChange = onPercentChangeToggled
                )
            },
            {
                SwitchWithText(
                    text = stringResource(Res.string.display_options_price_change),
                    checked = displayDiffOptionType.hasPriceChange,
                    onCheckedChange = onPriceChangeToggled
                )
            }
        )
    )

    CellUniversalLawrenceSection(
        listOf {
            SwitchWithText(
                text = stringResource(Res.string.sum_rounding),
                checked = isRoundingAmount,
                onCheckedChange = onRoundingAmountToggled
            )
        },
        Modifier.padding(top = 32.dp)
    )
}
