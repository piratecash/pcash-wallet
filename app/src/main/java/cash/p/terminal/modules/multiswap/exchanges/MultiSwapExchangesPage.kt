package cash.p.terminal.modules.multiswap.exchanges

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import cash.p.terminal.R
import cash.p.terminal.core.App
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.core.usecase.ResolveTransactionItemUseCase
import cash.p.terminal.modules.main.MainPage
import cash.p.terminal.modules.multiswap.MultiSwapLegInfo
import cash.p.terminal.modules.multiswap.SwapAmountDirection
import cash.p.terminal.modules.multiswap.SwapConfirmBalanceParams
import cash.p.terminal.modules.multiswap.SwapConfirmNavigation
import cash.p.terminal.modules.multiswap.SwapConfirmQuoteParams
import cash.p.terminal.modules.multiswap.SwapConfirmScreen
import cash.p.terminal.modules.multiswap.SwapSelectProviderScreen
import cash.p.terminal.modules.multiswap.SwapSelectProviderViewModel
import cash.p.terminal.modules.multiswap.SwapTransactionSettingsPage
import cash.p.terminal.modules.multiswap.providers.SwapProvidersRepository
import cash.p.terminal.modules.multiswap.exchange.MultiSwapExchangeScreen
import cash.p.terminal.modules.multiswap.exchange.MultiSwapExchangeViewModel
import cash.p.terminal.modules.multiswap.providersettings.SwapProvidersSettingsPage
import cash.p.terminal.modules.paycore.exchange.PayCoreExchangeDetailScreen
import cash.p.terminal.modules.paycore.exchange.PayCoreExchangeDetailViewModel
import cash.p.terminal.modules.transactionInfo.TransactionInfoPage
import cash.p.terminal.modules.transactions.TransactionsModule
import cash.p.terminal.modules.transactions.TransactionsViewModel
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpFrom
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.navigation.viewModelStoreOwnerForPage
import cash.p.terminal.ui_compose.components.HudHelper
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.reflect.KClass

class MultiSwapExchangesPage(
    val pendingMultiSwapId: String? = null,
    val payCoreDate: Long? = null,
) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val directPayCoreDate = payCoreDate?.takeIf { it > 0 }
        val viewModel = koinViewModel<MultiSwapExchangesViewModel>()

        PayCoreNavigationHandler(
            viewModel = viewModel,
            navigation = navigation,
        )

        when {
            pendingMultiSwapId != null -> ExchangeDetailContent(
                swapId = pendingMultiSwapId,
                detailPage = this,
                navigation = navigation,
            )
            directPayCoreDate != null -> PayCoreExchangeDetailContent(
                date = directPayCoreDate,
                onBack = navigation::navigateUp,
            )
            else -> ExchangesListContent(
                viewModel = viewModel,
                navigation = navigation,
                page = this,
            )
        }
    }
}

private class MultiSwapExchangeDetailPage(private val swapId: String) : HSPage() {
    @Composable
    override fun GetContent(navigation: HSNavigation) {
        ExchangeDetailContent(
            swapId = swapId,
            detailPage = this,
            navigation = navigation,
        )
    }
}

private class PayCoreExchangeDetailPage(private val date: Long) : HSPage() {
    @Composable
    override fun GetContent(navigation: HSNavigation) {
        PayCoreExchangeDetailContent(
            date = date,
            onBack = navigation::navigateUp,
        )
    }
}

private class MultiSwapLeg2SelectProviderPage(
    private val swapId: String,
    private val detailOwner: KClass<out HSPage>,
) : HSPage() {
    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel = navigation.exchangeViewModel(swapId, detailOwner)
        CloseDetailEffect(viewModel, navigation, this, detailOwner)
        val quotes = viewModel.leg2Quotes
        if (quotes.isEmpty()) {
            LaunchedEffect(Unit) {
                navigation.navigateUpFrom(this@MultiSwapLeg2SelectProviderPage)
            }
            return
        }
        val selectProviderViewModel = viewModel<SwapSelectProviderViewModel>(
            factory = SwapSelectProviderViewModel.Factory(quotes, SwapAmountDirection.In)
        )
        val swapProvidersRepository = remember { getKoinInstance<SwapProvidersRepository>() }
        val disabledIds by swapProvidersRepository.disabledIds.collectAsStateWithLifecycle()
        SwapSelectProviderScreen(
            onClickClose = navigation::navigateUpSafely,
            onClickSettings = {
                navigation.slideFromRight(SwapProvidersSettingsPage())
            },
            quotes = selectProviderViewModel.uiState.quoteViewItems,
            currentQuote = viewModel.selectedLeg2Quote,
            mandatoryProviderIds = SwapProvidersRepository.MANDATORY_IDS,
            disabledProviderIds = disabledIds,
            sortType = selectProviderViewModel.uiState.sortType,
            onSortTypeChange = selectProviderViewModel::setSortType,
            onToggleProvider = swapProvidersRepository::setDisabled,
            swapRates = {
                HudHelper.vibrate(App.instance)
                selectProviderViewModel.swapRates()
            },
            onSelectQuote = { quote ->
                viewModel.onSelectLeg2Quote(quote)
                navigation.navigateUpSafely()
            }
        )
    }
}

private class MultiSwapLeg2ConfirmPage(
    private val swapId: String,
    private val detailOwner: KClass<out HSPage>,
) : HSPage() {
    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel = navigation.exchangeViewModel(swapId, detailOwner)
        CloseDetailEffect(viewModel, navigation, this, detailOwner)
        val quote = viewModel.selectedLeg2Quote ?: run {
            LaunchedEffect(Unit) { navigation.navigateUpFrom(this@MultiSwapLeg2ConfirmPage) }
            return
        }
        val balanceState by viewModel.leg2BalanceStateFlow.collectAsStateWithLifecycle(
            viewModel.leg2BalanceStateFlow.value
        )
        SwapConfirmScreen(
            navigation = SwapConfirmNavigation(navigation, MultiSwapExchangesPage::class),
            quoteParams = SwapConfirmQuoteParams(
                quote = quote,
                settings = emptyMap(),
                direction = SwapAmountDirection.In,
                requestedAmountOut = null,
                multiSwapLegInfo = MultiSwapLegInfo.Leg2(swapId),
            ),
            balanceParams = SwapConfirmBalanceParams(
                provider = quote.provider,
                displayBalance = balanceState.displayBalance,
                balanceHidden = viewModel.leg2BalanceHidden,
                feeToken = balanceState.feeToken,
                feeCoinBalance = balanceState.feeCoinBalance,
            ),
            onToggleHideBalance = viewModel::toggleLeg2BalanceHidden,
            onReapprove = {},
            onOpenSettings = {
                navigation.slideFromRight(SwapTransactionSettingsPage(MultiSwapLeg2ConfirmPage::class))
            },
        )
    }
}

@Composable
private fun HSNavigation.exchangeViewModel(
    swapId: String,
    detailOwner: KClass<out HSPage>,
): MultiSwapExchangeViewModel = koinViewModel(
    viewModelStoreOwner = viewModelStoreOwnerForPage(detailOwner),
) { parametersOf(swapId) }

// The detail and its leg-2 pages close together, as they did inside one nested host.
@Composable
private fun CloseDetailEffect(
    viewModel: MultiSwapExchangeViewModel,
    navigation: HSNavigation,
    caller: HSPage,
    detailOwner: KClass<out HSPage>,
) {
    LaunchedEffect(viewModel.closeScreen) {
        // An exiting caller must not pop another instance of detailOwner.
        if (viewModel.closeScreen && navigation.backStack.any { it === caller }) {
            navigation.removeLastUntil(detailOwner, inclusive = true)
        }
    }
}

@Composable
private fun PayCoreNavigationHandler(
    viewModel: MultiSwapExchangesViewModel,
    navigation: HSNavigation,
) {
    LaunchedEffect(viewModel) {
        viewModel.events.collect { effect ->
            when (effect) {
                is PayCoreNavigationEffect.OpenPayCoreDetail -> {
                    navigation.slideFromRight(PayCoreExchangeDetailPage(effect.date))
                }
            }
        }
    }
}

@Composable
private fun ExchangesListContent(
    viewModel: MultiSwapExchangesViewModel,
    navigation: HSNavigation,
    page: HSPage,
) {
    LaunchedEffect(Unit) {
        snapshotFlow { viewModel.uiState.items }
            .drop(1)
            .filter { it.isEmpty() }
            .collect { navigation.navigateUpFrom(page) }
    }

    MultiSwapExchangesScreen(
        uiState = viewModel.uiState,
        onSelect = { item ->
            if (item.isPayCore) {
                viewModel.onPayCoreItemClick(item)
            } else {
                navigation.slideFromRight(MultiSwapExchangeDetailPage(item.id))
            }
        },
        onDelete = viewModel::onDelete,
        onPayCoreSelectBank = viewModel::onPayCoreItemClick,
        onBack = navigation::navigateUpSafely,
    )
}

@Composable
private fun ExchangeDetailContent(
    swapId: String,
    detailPage: HSPage,
    navigation: HSNavigation,
) {
    val detailOwner = detailPage::class
    val transactionsViewModel = viewModel<TransactionsViewModel>(
        viewModelStoreOwner = navigation.viewModelStoreOwnerForPage(MainPage::class),
        factory = TransactionsModule.Factory(),
    )
    val viewModel = koinViewModel<MultiSwapExchangeViewModel> {
        parametersOf(swapId)
    }
    val resolveTransactionItem = koinInject<ResolveTransactionItemUseCase>()
    val coroutineScope = rememberCoroutineScope()

    CloseDetailEffect(viewModel, navigation, detailPage, detailOwner)

    LifecycleResumeEffect(Unit) {
        viewModel.resumeTimer()
        onPauseOrDispose { viewModel.pauseTimer() }
    }
    MultiSwapExchangeScreen(
        uiState = viewModel.uiState,
        timeRemainingProgress = { viewModel.timeRemainingProgress },
        onSwap = {
            val action = viewModel.uiState?.actionCreate
            if (action != null && !action.inProgress) {
                viewModel.createMissingWallets(action.tokensToAdd)
            } else if (action == null && viewModel.selectedLeg2Quote != null) {
                navigation.slideFromRight(MultiSwapLeg2ConfirmPage(swapId, detailOwner))
            }
        },
        swapButtonTitle = when {
            viewModel.uiState?.actionCreate?.inProgress == true ->
                stringResource(R.string.swap_creating_wallets)

            viewModel.uiState?.actionCreate != null ->
                stringResource(R.string.swap_create_wallets)

            else -> stringResource(R.string.Swap)
        },
        onRefresh = viewModel::refreshQuotes,
        onContinueLater = viewModel::onContinueLater,
        onDeleteAndClose = viewModel::onDeleteAndClose,
        onBack = navigation::navigateUpSafely,
        onClickProvider = {
            if (viewModel.leg2Quotes.isNotEmpty()) {
                navigation.slideFromBottom(MultiSwapLeg2SelectProviderPage(swapId, detailOwner))
            }
        },
        onClickLeg1 = {
            coroutineScope.launch {
                val recordUid = viewModel.leg1NavigationRecordUid ?: return@launch
                val transactionItem = resolveTransactionItem(recordUid) ?: return@launch
                transactionsViewModel.tmpItemToShow = transactionItem
                navigation.slideFromBottom(TransactionInfoPage())
            }
        },
    )
}

@Composable
private fun PayCoreExchangeDetailContent(
    date: Long,
    onBack: () -> Unit,
) {
    val viewModel = koinViewModel<PayCoreExchangeDetailViewModel> {
        parametersOf(date)
    }
    PayCoreExchangeDetailScreen(
        uiState = viewModel.uiState,
        onBack = onBack,
    )
}
