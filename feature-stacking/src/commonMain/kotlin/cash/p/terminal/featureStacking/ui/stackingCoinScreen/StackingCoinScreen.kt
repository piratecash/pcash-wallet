package cash.p.terminal.featureStacking.ui.stackingCoinScreen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import cash.p.terminal.featureStacking.ui.staking.StackingType
import cash.p.terminal.premium.data.config.PremiumConfig
import cash.p.terminal.premium.domain.usecase.PremiumType
import cash.p.terminal.resources.Balance_Title
import cash.p.terminal.resources.Coin_Info
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.buy_cosanta
import cash.p.terminal.resources.buy_pirate
import cash.p.terminal.resources.buy_to_activate_premium
import cash.p.terminal.resources.estimated_annual_interest
import cash.p.terminal.resources.how_my_income_grows
import cash.p.terminal.resources.ic_attention_24
import cash.p.terminal.resources.ic_calculator
import cash.p.terminal.resources.ic_chart_24
import cash.p.terminal.resources.ic_info_20
import cash.p.terminal.resources.ic_no_investment
import cash.p.terminal.resources.ic_swap_24
import cash.p.terminal.resources.no_active_stacking
import cash.p.terminal.resources.no_active_stacking_cosanta_buy_more_description
import cash.p.terminal.resources.no_active_stacking_cosanta_description
import cash.p.terminal.resources.no_active_stacking_pirate_buy_more_description
import cash.p.terminal.resources.no_active_stacking_pirate_description
import cash.p.terminal.resources.premium_available
import cash.p.terminal.resources.premium_demo_days_left
import cash.p.terminal.resources.stacking_calculator
import cash.p.terminal.resources.staking_in_hours
import cash.p.terminal.resources.staking_next_accrual
import cash.p.terminal.resources.staking_next_accrual_info_body_cosanta
import cash.p.terminal.resources.staking_next_accrual_info_body_pirate
import cash.p.terminal.resources.staking_next_accrual_info_title
import cash.p.terminal.resources.staking_next_payout
import cash.p.terminal.resources.staking_next_payout_info_body
import cash.p.terminal.resources.staking_unpaid_info_body_cosanta
import cash.p.terminal.resources.staking_unpaid_info_body_pirate
import cash.p.terminal.resources.staking_unpaid_info_title
import cash.p.terminal.resources.total_balance
import cash.p.terminal.resources.total_income
import cash.p.terminal.resources.unpaid
import cash.p.terminal.resources.waiting_for_stacking
import cash.p.terminal.ui_compose.components.ButtonPrimaryCircle
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellowWithIcon
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HSCircularProgressIndicator
import cash.p.terminal.ui_compose.components.HSSwipeRefresh
import cash.p.terminal.ui_compose.components.HsIconButton
import cash.p.terminal.ui_compose.components.HsImage
import cash.p.terminal.ui_compose.components.InfoBottomSheet
import cash.p.terminal.ui_compose.components.LocalHudMessenger
import cash.p.terminal.ui_compose.components.TextImportantWarning
import cash.p.terminal.ui_compose.components.TitleAndTwoValuesCell
import cash.p.terminal.ui_compose.components.TitleAndValueCell
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.alternativeImageUrl
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.imagePlaceholder
import cash.p.terminal.wallet.imageUrl
import io.horizontalsystems.chartview.chart.ChartModule
import io.horizontalsystems.chartview.chart.ChartUiState
import io.horizontalsystems.chartview.chart.SelectedItem
import io.horizontalsystems.chartview.ui.Chart
import io.horizontalsystems.core.models.HsTimePeriod
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import java.math.BigDecimal

@Composable
internal fun StackingCoinScreen(
    onBuyClicked: (Token) -> Unit,
    onCalculatorClicked: () -> Unit,
    onChartClicked: (String) -> Unit,
    viewModel: StackingCoinViewModel,
    chartViewModel: StackingCoinChartViewModel
) {
    LifecycleResumeEffect(viewModel) {
        val job = viewModel.loadData()
        onPauseOrDispose { job.cancel() }
    }
    LaunchedEffect(viewModel.uiState.value.receiveAddress) {
        viewModel.uiState.value.receiveAddress?.let(chartViewModel::setReceiveAddress)
    }
    HSSwipeRefresh(
        refreshing = viewModel.uiState.value.isRefreshing,
        onRefresh = {
            viewModel.refresh()
            chartViewModel.refresh()
        }
    ) {
        PirateCoinScreenContent(
            uiState = viewModel.uiState.value,
            premiumType = viewModel.premiumType,
            daysPremiumLeft = viewModel.daysPremiumLeft,
            onBuyClicked = onBuyClicked,
            onCalculatorClicked = onCalculatorClicked,
            onChartClicked = onChartClicked,
            graphUIState = chartViewModel.uiState,
            getSelectedPointCallback = chartViewModel::getSelectedPoint,
            onSelectChartInterval = chartViewModel::onSelectChartInterval,
            onToggleBalanceVisibility = viewModel::toggleBalanceVisibility
        )
    }
}

@Composable
internal fun PirateCoinScreenContent(
    uiState: StackingCoinUIState,
    premiumType: PremiumType,
    daysPremiumLeft: Int,
    onBuyClicked: (Token) -> Unit,
    onCalculatorClicked: () -> Unit,
    onChartClicked: (String) -> Unit,
    graphUIState: ChartUiState,
    getSelectedPointCallback: (SelectedItem) -> ChartModule.ChartHeaderView,
    onSelectChartInterval: (HsTimePeriod?) -> Unit,
    onToggleBalanceVisibility: () -> Unit
) {
    Column {
        if (uiState.loading) {
            LoadingScreen()
        } else {
            if (uiState.balance == BigDecimal.ZERO && uiState.payoutItems.isEmpty()) {
                NoCoins(
                    uiState = uiState,
                    premiumType = premiumType,
                    onBuyClicked = {
                        uiState.token?.let { onBuyClicked(it) }
                    }
                )
            } else {
                PirateCoinScreenWithGraph(
                    uiState = uiState,
                    premiumType = premiumType,
                    daysPremiumLeft = daysPremiumLeft,
                    onBuyClicked = {
                        uiState.token?.let { onBuyClicked(it) }
                    },
                    onCalculatorClicked = onCalculatorClicked,
                    onChartClicked = {
                        uiState.token?.let { onChartClicked(it.coin.uid) }
                    },
                    graphUIState = graphUIState,
                    getSelectedPointCallback = getSelectedPointCallback,
                    onSelectChartInterval = onSelectChartInterval,
                    onToggleBalanceVisibility = onToggleBalanceVisibility
                )
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize()) {
        HSCircularProgressIndicator(
            modifier = Modifier.align(
                Alignment.Center
            )
        )
    }
}

@Composable
private fun NoCoins(
    uiState: StackingCoinUIState,
    premiumType: PremiumType,
    onBuyClicked: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 52.dp),
    ) {
        val stringResId = if (uiState.stackingType == StackingType.PCASH) {
            Res.string.no_active_stacking_pirate_description
        } else {
            Res.string.no_active_stacking_cosanta_description
        }
        Spacer(modifier = Modifier.weight(1f))
        Image(
            painter = painterResource(Res.drawable.ic_no_investment),
            modifier = Modifier.size(100.dp),
            contentDescription = null
        )
        Text(
            style = ComposeAppTheme.typography.body.copy(
                fontWeight = FontWeight.Medium
            ),
            color = ComposeAppTheme.colors.textPrimary,
            text = stringResource(Res.string.no_active_stacking),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp)
        )
        Text(
            style = ComposeAppTheme.typography.subhead2,
            color = ComposeAppTheme.colors.textSecondary,
            text = stringResource(stringResId)
                .replace("_annual_interest_", uiState.annualInterest),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
        if (premiumType == PremiumType.NONE) {
            val buyPremiumStringResId = stringResource(Res.string.buy_to_activate_premium).format(
                if (uiState.stackingType == StackingType.PCASH) {
                    "${PremiumConfig.MIN_PREMIUM_AMOUNT_PIRATE} ${StackingType.PCASH.value}"
                } else {
                    "${PremiumConfig.MIN_PREMIUM_AMOUNT_COSANTA} ${StackingType.COSANTA.value}"
                }
            )
            Text(
                style = ComposeAppTheme.typography.subhead2,
                color = ComposeAppTheme.colors.textSecondary,
                text = buyPremiumStringResId,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
        ButtonPrimaryYellowWithIcon(
            title = stringResource(
                if (uiState.stackingType == StackingType.PCASH) {
                    Res.string.buy_pirate
                } else {
                    Res.string.buy_cosanta
                }
            ),
            onClick = onBuyClicked,
            icon = painterResource(Res.drawable.ic_swap_24),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            enabled = !uiState.isWatchAccount
        )
        Spacer(modifier = Modifier.weight(3f))
    }
}

@Composable
private fun PirateCoinScreenWithGraph(
    uiState: StackingCoinUIState,
    premiumType: PremiumType,
    daysPremiumLeft: Int,
    onBuyClicked: () -> Unit,
    onCalculatorClicked: () -> Unit,
    onChartClicked: () -> Unit,
    graphUIState: ChartUiState,
    getSelectedPointCallback: (SelectedItem) -> ChartModule.ChartHeaderView,
    onSelectChartInterval: (HsTimePeriod?) -> Unit,
    onToggleBalanceVisibility: () -> Unit
) {
    LazyColumn {
        item {
            CoinBalanceBlock(
                coin = uiState.token?.coin,
                balanceStr = uiState.balanceStr,
                secondaryAmount = uiState.secondaryAmount.orEmpty(),
                visible = !uiState.balanceHidden,
                onToggleBalanceVisibility = onToggleBalanceVisibility
            )
        }
        warningCard(uiState)
        item {
            Row(
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ButtonPrimaryYellowWithIcon(
                    title = stringResource(
                        if (uiState.stackingType == StackingType.PCASH) {
                            Res.string.buy_pirate
                        } else {
                            Res.string.buy_cosanta
                        }
                    ),
                    onClick = onBuyClicked,
                    icon = painterResource(Res.drawable.ic_swap_24),
                    modifier = Modifier
                        .weight(1f),
                    enabled = !uiState.isWatchAccount
                )
                ButtonPrimaryCircle(
                    icon = Res.drawable.ic_calculator,
                    contentDescription = stringResource(Res.string.stacking_calculator),
                    onClick = onCalculatorClicked
                )
                ButtonPrimaryCircle(
                    icon = Res.drawable.ic_chart_24,
                    contentDescription = stringResource(Res.string.Coin_Info),
                    onClick = onChartClicked
                )
            }
            TotalSection(uiState, premiumType, daysPremiumLeft, Modifier.padding(vertical = 24.dp))
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(ComposeAppTheme.colors.borderDivider)
            )
            Text(
                style = ComposeAppTheme.typography.body,
                color = ComposeAppTheme.colors.textPrimary,
                text = stringResource(Res.string.how_my_income_grows),
                modifier = Modifier.padding(vertical = 14.dp, horizontal = 16.dp)
            )
            Chart(
                uiState = graphUIState,
                getSelectedPointCallback = getSelectedPointCallback,
                onSelectChartInterval = onSelectChartInterval
            )
        }
        item {
            Spacer(
                modifier = Modifier
                    .padding(top = 24.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(ComposeAppTheme.colors.borderDivider)
            )
        }
        payoutList(
            payoutItemsMap = uiState.payoutItems
        )
    }
}

@Composable
private fun TotalSection(
    uiState: StackingCoinUIState,
    premiumType: PremiumType,
    daysPremiumLeft: Int,
    modifier: Modifier
) {
    val waitingForStackingPlaceholder = if (uiState.isWaitingForStacking()) "-" else null
    CellUniversalLawrenceSection(
        composableItems = buildList {
            add {
                val totalIncome =
                    if (uiState.balanceHidden) "*****" else "${uiState.totalIncomeStr} ${uiState.token?.coin?.code}"
                TitleAndTwoValuesCell(
                    title = stringResource(Res.string.total_income),
                    value = waitingForStackingPlaceholder ?: totalIncome,
                    value2 = if (uiState.isWaitingForStacking()) {
                        null
                    } else {
                        if (uiState.balanceHidden) "*****" else uiState.totalIncomeSecondary.orEmpty()
                    }
                )
            }
            add {
                val unpaid =
                    if (uiState.balanceHidden) "*****" else "${uiState.unpaidStr} ${uiState.token?.coin?.code}"
                StackingInfoCell(
                    title = stringResource(Res.string.unpaid),
                    value = waitingForStackingPlaceholder ?: unpaid,
                    value2 = if (uiState.isWaitingForStacking()) {
                        null
                    } else {
                        if (uiState.balanceHidden) "*****" else uiState.unpaidSecondary.orEmpty()
                    },
                    infoTitle = stringResource(Res.string.staking_unpaid_info_title),
                    infoText = stringResource(unpaidInfoBodyRes(uiState.stackingType)),
                    infoContentDescription = stringResource(Res.string.staking_unpaid_info_title),
                )
            }
            add {
                TitleAndValueCell(
                    title = stringResource(Res.string.estimated_annual_interest),
                    value = uiState.annualInterest,
                    modifier = Modifier.height(48.dp)
                )
            }
            uiState.hoursUntilNextAccrual?.let { hours ->
                add {
                    StackingInfoCell(
                        title = stringResource(Res.string.staking_next_accrual),
                        value = pluralStringResource(Res.plurals.staking_in_hours, hours).format(hours),
                        infoTitle = stringResource(Res.string.staking_next_accrual_info_title),
                        infoText = stringResource(nextAccrualInfoBodyRes(uiState.stackingType)),
                        infoContentDescription = stringResource(Res.string.staking_next_accrual_info_title),
                    )
                }
            }
            uiState.nextPayoutText?.let { nextPayoutText ->
                add {
                    StackingInfoCell(
                        title = stringResource(Res.string.staking_next_payout),
                        value = nextPayoutText,
                        infoTitle = stringResource(Res.string.staking_unpaid_info_title),
                        infoText = stringResource(Res.string.staking_next_payout_info_body),
                        infoContentDescription = stringResource(Res.string.staking_unpaid_info_title),
                    )
                }
            }
            if (!uiState.balanceHidden && premiumType.isPremium()) {
                val text = when (premiumType) {
                    PremiumType.PIRATE -> premiumText(
                        stringResource(Res.string.premium_available),
                        StackingType.PCASH,
                        PremiumConfig.MIN_PREMIUM_AMOUNT_PIRATE
                    )

                    PremiumType.COSA -> premiumText(
                        stringResource(Res.string.premium_available),
                        StackingType.COSANTA,
                        PremiumConfig.MIN_PREMIUM_AMOUNT_COSANTA
                    )

                    PremiumType.TRIAL -> "⭐ " +
                        pluralStringResource(Res.plurals.premium_demo_days_left, daysPremiumLeft)
                            .format(daysPremiumLeft)

                    PremiumType.NONE -> "" // won't happen
                }
                add {
                    TitleAndValueCell(
                        title = text,
                        value = "",
                        minHeight = 48.dp
                    )
                }
            }
        },
        modifier = modifier
    )
}

@Composable
private fun premiumText(
    prefix: String,
    stacking: StackingType,
    minAmount: Int
) = "$prefix (${stringResource(Res.string.Balance_Title)} ${stacking.value} ${minAmount}+)"

@Composable
private fun StackingInfoCell(
    title: String,
    value: String,
    infoTitle: String,
    infoText: String,
    infoContentDescription: String,
    value2: String? = null,
) {
    var showInfoSheet by rememberSaveable { mutableStateOf(false) }

    if (value2 == null) {
        TitleAndValueCell(
            title = title,
            value = value,
            minHeight = 48.dp,
            titleAction = {
                StackingInfoActionButton(
                    contentDescription = infoContentDescription,
                    onClick = { showInfoSheet = true }
                )
            }
        )
    } else {
        TitleAndTwoValuesCell(
            title = title,
            value = value,
            value2 = value2,
            titleAction = {
                StackingInfoActionButton(
                    contentDescription = infoContentDescription,
                    onClick = { showInfoSheet = true }
                )
            },
        )
    }

    if (showInfoSheet) {
        InfoBottomSheet(
            title = infoTitle,
            text = infoText,
            onDismiss = { showInfoSheet = false },
        )
    }
}

@Composable
private fun StackingInfoActionButton(
    contentDescription: String,
    onClick: () -> Unit,
) {
    HsIconButton(
        onClick = onClick,
        modifier = Modifier.size(20.dp),
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_info_20),
            contentDescription = contentDescription,
            tint = ComposeAppTheme.colors.iconSecondary,
        )
    }
}

private fun unpaidInfoBodyRes(stackingType: StackingType): StringResource = when (stackingType) {
    StackingType.PCASH -> Res.string.staking_unpaid_info_body_pirate
    StackingType.COSANTA -> Res.string.staking_unpaid_info_body_cosanta
}

private fun nextAccrualInfoBodyRes(stackingType: StackingType): StringResource = when (stackingType) {
    StackingType.PCASH -> Res.string.staking_next_accrual_info_body_pirate
    StackingType.COSANTA -> Res.string.staking_next_accrual_info_body_cosanta
}

private fun LazyListScope.warningCard(uiState: StackingCoinUIState) {
    if (!uiState.isWaitingForStacking()) return
    val notEnoughCoins = uiState.balance < uiState.minStackingAmount

    item {
        val stringDescription = if (notEnoughCoins) {
            stringResource(
                if (uiState.stackingType == StackingType.PCASH) {
                    Res.string.no_active_stacking_pirate_buy_more_description
                } else {
                    Res.string.no_active_stacking_cosanta_buy_more_description
                }
            ).format((uiState.minStackingAmount - uiState.balance).toPlainString())
                .replace("_annual_interest_", uiState.annualInterest)
        } else {
            val hours = uiState.stackingType.maxHoursUntilFirstAccrual
            stringResource(Res.string.waiting_for_stacking).format(hours)
        }
        TextImportantWarning(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
            title = stringResource(Res.string.no_active_stacking),
            text = stringDescription,
            icon = Res.drawable.ic_attention_24
        )
    }
}

@Composable
private fun CoinBalanceBlock(
    coin: Coin?,
    balanceStr: String,
    secondaryAmount: String,
    visible: Boolean,
    onToggleBalanceVisibility: () -> Unit,
    modifier: Modifier = Modifier
) {
    val balanceWithCoinCode = "$balanceStr ${coin?.code}"
    val hud = LocalHudMessenger.current
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        VSpacer(height = 26.dp)
        Text(
            text = stringResource(Res.string.total_balance),
            color = ComposeAppTheme.colors.textPrimary,
            style = ComposeAppTheme.typography.body,
            textAlign = TextAlign.Center,
        )
        VSpacer(height = 14.dp)
        HsImage(
            url = coin?.imageUrl,
            alternativeUrl = coin?.alternativeImageUrl,
            placeholder = coin?.imagePlaceholder,
            modifier = modifier
                .size(56.dp)
                .clip(CircleShape)
        )
        VSpacer(height = 12.dp)
        Text(
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        onToggleBalanceVisibility()
                        hud.vibrate()
                    }
                ),
            text = if (visible) balanceWithCoinCode else "*****",
            color = ComposeAppTheme.colors.textPrimary,
            style = ComposeAppTheme.typography.title2R,
            textAlign = TextAlign.Center,
        )
        VSpacer(height = 6.dp)
        Text(
            text = if (visible) secondaryAmount else "*****",
            color = ComposeAppTheme.colors.textSecondary,
            style = ComposeAppTheme.typography.body,
            maxLines = 1,
        )
    }
}
