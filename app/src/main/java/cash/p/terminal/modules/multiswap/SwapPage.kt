package cash.p.terminal.modules.multiswap

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cash.p.terminal.R
import cash.p.terminal.entities.CoinValue
import cash.p.terminal.modules.multiswap.action.ActionCreate
import cash.p.terminal.modules.multiswap.action.ISwapProviderAction
import cash.p.terminal.modules.fee.FeeInfoSection
import cash.p.terminal.modules.fee.QuoteInfoRow
import cash.p.terminal.modules.multiswap.providers.IMultiSwapProvider
import cash.p.terminal.modules.multiswap.providers.isOffChain
import cash.p.terminal.modules.multiswap.settings.SwapTransactionSettingsScreen
import cash.p.terminal.modules.multiswap.exchange.MultiSwapExchangeScreen
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpFrom
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.navigation.viewModelStoreOwnerForPage
import cash.p.terminal.ui.compose.Keyboard
import cash.p.terminal.ui.compose.components.CardsSwapInfo
import cash.p.terminal.ui.compose.components.CoinImage
import cash.p.terminal.ui.compose.components.HSRow
import cash.p.terminal.ui.compose.components.SuggestionsBar
import cash.p.terminal.ui.compose.observeKeyboardState
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryDefault
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui.compose.components.SwapDirectionIndicator
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.MenuItemTimeoutIndicator
import cash.p.terminal.ui_compose.components.TextImportantError
import cash.p.terminal.ui_compose.components.TextImportantWarning
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_grey
import cash.p.terminal.ui_compose.components.headline1_grey
import cash.p.terminal.ui_compose.components.micro_grey
import cash.p.terminal.ui_compose.components.subhead1_jacob
import cash.p.terminal.ui_compose.components.subhead1_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.subhead2_leah
import cash.p.terminal.ui_compose.theme.ColoredTextStyle
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.badge
import cash.p.terminal.wallet.useCases.WalletUseCase
import io.horizontalsystems.core.entities.Currency
import cash.p.terminal.core.App
import cash.p.terminal.core.ethereum.toCautionViewItem
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.modules.multiswap.providers.SwapProvidersRepository
import cash.p.terminal.modules.evmfee.Cautions
import cash.p.terminal.modules.multiswap.providersettings.SwapProvidersSettingsPage
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui_compose.components.MenuItem
import io.horizontalsystems.core.toBigDecimalOrNullExt
import java.math.BigDecimal
import java.net.UnknownHostException
import cash.p.terminal.modules.managewallets.ManageWalletsModule
import cash.p.terminal.modules.managewallets.ManageWalletsViewModel
import cash.p.terminal.modules.enablecoin.restoresettings.RestoreSettingsViewModel
import cash.p.terminal.modules.enablecoin.restoresettings.openRestoreSettingsDialog
import cash.p.terminal.ui_compose.components.HudHelper
import cash.p.terminal.ui_compose.components.InfoBottomSheet
import cash.p.terminal.modules.multiswap.settings.SwapSettingsScreen
import cash.p.terminal.modules.offline.OfflineBlockedBottomSheet
import cash.p.terminal.modules.offline.OfflineOperationGate
import cash.p.terminal.modules.paycore.PayCoreAssets
import cash.p.terminal.modules.paycore.PayCoreQuote
import cash.p.terminal.modules.paycore.PayCoreSelectBankAction
import cash.p.terminal.modules.paycore.PayCoreVerificationAction
import cash.p.terminal.modules.paycore.payment.PayCorePaymentDisplayParams
import cash.p.terminal.modules.paycore.payment.PayCorePaymentParams
import cash.p.terminal.modules.paycore.payment.PayCorePaymentScreen
import cash.p.terminal.modules.paycore.payment.PayCorePaymentViewModel
import cash.p.terminal.modules.paycore.verification.PayCoreVerificationScreen
import androidx.annotation.Keep
import cash.p.terminal.modules.paycore.PayCoreNetworkMapper.toTicker
import cash.p.terminal.modules.paycore.PayCoreTicker
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.reflect.KClass
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

class SwapPage(val tokenIn: Token? = null, val tokenOut: Token? = null) : HSPage() {
    @Composable
    override fun GetContent(navigation: HSNavigation) {
        SwapScreen(navigation = navigation, tokenIn = tokenIn, tokenOut = tokenOut)
    }
}

/** The flow's SwapViewModel, created by [SwapPage]. */
@Composable
private fun HSNavigation.swapViewModel(): SwapViewModel = viewModel(
    viewModelStoreOwner = viewModelStoreOwnerForPage(SwapPage::class),
    factory = SwapViewModel.Factory(null, null),
)

private class SwapSelectCoinPage(private val direction: SwapCoinDirection) : HSPage() {
    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel = navigation.swapViewModel()
        val otherToken = viewModel.uiState.otherToken(direction)
        val titleResId = direction.titleResId()
        val offlineGate = remember { getKoinInstance<OfflineOperationGate>() }
        val walletManager = remember { getKoinInstance<IWalletManager>() }
        var blockedWallet by remember { mutableStateOf<Wallet?>(null) }

        val selectToken: (Token) -> Unit = { token ->
            when (direction) {
                SwapCoinDirection.From -> viewModel.onSelectTokenIn(token)
                SwapCoinDirection.To -> viewModel.onSelectTokenOut(token)
            }
            navigation.navigateUpSafely()
        }

        SwapSelectCoinScreen(
            navigation = navigation,
            token = otherToken,
            title = stringResource(id = titleResId)
        ) { token ->
            // Only the spent side is gated: an offline output token cannot move funds.
            val offlineWallet = if (direction == SwapCoinDirection.From) {
                walletManager.activeWallets
                    .firstOrNull { it.token == token }
                    ?.takeIf(offlineGate::isBlocked)
            } else {
                null
            }
            if (offlineWallet != null) blockedWallet = offlineWallet else selectToken(token)
        }

        blockedWallet?.let { wallet ->
            OfflineBlockedBottomSheet(
                wallet = wallet,
                onWentOnline = {
                    blockedWallet = null
                    selectToken(wallet.token)
                },
                onDismiss = { blockedWallet = null },
            )
        }
    }
}

private class SwapSelectProviderPage : HSPage() {
    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel = navigation.swapViewModel()
        val quotes = viewModel.uiState.quotes
        if (quotes.isEmpty()) {
            LaunchedEffect(Unit) {
                navigation.navigateUpFrom(this@SwapSelectProviderPage)
            }
            return
        }
        val selectProviderViewModel = viewModel<SwapSelectProviderViewModel>(
            factory = SwapSelectProviderViewModel.Factory(
                quotes = quotes,
                direction = viewModel.uiState.direction,
                quoteUpdates = viewModel.quotesFlow,
            )
        )
        val swapProvidersRepository = remember { getKoinInstance<SwapProvidersRepository>() }
        val disabledIds by swapProvidersRepository.disabledIds.collectAsStateWithLifecycle()
        SwapSelectProviderScreen(
            onClickClose = navigation::navigateUpSafely,
            onClickSettings = {
                navigation.slideFromRight(SwapProvidersSettingsPage())
            },
            quotes = selectProviderViewModel.uiState.quoteViewItems,
            currentQuote = viewModel.uiState.quote,
            mandatoryProviderIds = SwapProvidersRepository.MANDATORY_IDS,
            disabledProviderIds = disabledIds,
            sortType = selectProviderViewModel.uiState.sortType,
            onSortTypeChange = selectProviderViewModel::setSortType,
            onToggleProvider = swapProvidersRepository::setDisabled,
            swapRates = {
                HudHelper.vibrate(App.instance)
                selectProviderViewModel.swapRates()
            },
            onSelectQuote = viewModel::onSelectQuote
        )
    }
}

private class SwapRouteInfoPage : HSPage() {
    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel = navigation.swapViewModel()
        val uiState = viewModel.uiState
        val routeState = viewModel.multiSwapRouteInfoUiState(uiState) ?: run {
            LaunchedEffect(Unit) { navigation.navigateUpFrom(this@SwapRouteInfoPage) }
            return
        }
        LifecycleResumeEffect(uiState.timeout) {
            viewModel.refreshExpiredMultiSwapRoute()
            onPauseOrDispose { }
        }
        MultiSwapExchangeScreen(
            uiState = routeState,
            timeRemainingProgress = { viewModel.timeRemainingProgress },
            onSwap = {
                if (viewModel.canContinueMultiSwapRoute()) navigation.slideFromRight(SwapConfirmPage())
            },
            onRefresh = viewModel::refreshMultiSwapRoute,
            onContinueLater = navigation::navigateUp,
            onDeleteAndClose = navigation::navigateUp,
            onBack = navigation::navigateUp,
            onClickProvider = { navigation.slideFromBottom(SwapSelectLeg2ProviderPage()) },
        )
    }
}

private class SwapSelectLeg2ProviderPage : HSPage() {
    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel = navigation.swapViewModel()
        val uiState = viewModel.uiState
        val route = uiState.multiSwapRoute
        if (route == null || !viewModel.canContinueMultiSwapRoute() || route.leg2Quotes.isEmpty()) {
            LaunchedEffect(Unit) { navigation.navigateUpFrom(this@SwapSelectLeg2ProviderPage) }
            return
        }
        val selectProviderViewModel = viewModel<SwapSelectProviderViewModel>(
            factory = SwapSelectProviderViewModel.Factory(route.leg2Quotes, SwapAmountDirection.In),
        )
        val swapProvidersRepository = remember { getKoinInstance<SwapProvidersRepository>() }
        val disabledIds by swapProvidersRepository.disabledIds.collectAsStateWithLifecycle()
        SwapSelectProviderScreen(
            onClickClose = navigation::navigateUpSafely,
            onClickSettings = { navigation.slideFromRight(SwapProvidersSettingsPage()) },
            quotes = selectProviderViewModel.uiState.quoteViewItems,
            currentQuote = route.selectedLeg2Quote,
            mandatoryProviderIds = SwapProvidersRepository.MANDATORY_IDS,
            disabledProviderIds = disabledIds,
            sortType = selectProviderViewModel.uiState.sortType,
            onSortTypeChange = selectProviderViewModel::setSortType,
            onToggleProvider = swapProvidersRepository::setDisabled,
            swapRates = selectProviderViewModel::swapRates,
            onSelectQuote = {
                if (viewModel.onSelectLeg2Quote(it)) navigation.navigateUpSafely()
            },
        )
    }
}

private class SwapConfirmPage : HSPage() {
    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel = navigation.swapViewModel()
        val multiSwapLegInfo = remember { buildMultiSwapLeg1Info(viewModel) }
        val quote = remember { viewModel.getCurrentQuote() } ?: run {
            LaunchedEffect(Unit) { navigation.navigateUpFrom(this@SwapConfirmPage) }
            return
        }
        val settings = remember { viewModel.getSettings() }
        SwapConfirmScreen(
            navigation = SwapConfirmNavigation(navigation, SwapPage::class),
            quoteParams = SwapConfirmQuoteParams(
                quote = quote,
                settings = settings,
                direction = viewModel.uiState.direction,
                requestedAmountOut = viewModel.uiState.requestedAmountOut,
                multiSwapLegInfo = multiSwapLegInfo,
            ),
            balanceParams = SwapConfirmBalanceParams(
                provider = viewModel.uiState.quote?.provider,
                displayBalance = viewModel.uiState.displayBalance,
                balanceHidden = viewModel.uiState.balanceHidden,
                feeToken = viewModel.uiState.feeToken,
                feeCoinBalance = viewModel.uiState.feeCoinBalance,
            ),
            onToggleHideBalance = viewModel::toggleHideBalance,
            onReapprove = {
                if (navigation.navigateUpSafely()) viewModel.reQuote()
            },
            onOpenSettings = {
                navigation.slideFromRight(SwapTransactionSettingsPage(SwapConfirmPage::class))
            },
        )
    }
}

private class SwapSettingsPage : HSPage() {
    @Composable
    override fun GetContent(navigation: HSNavigation) {
        SwapSettingsScreen(
            navigation = navigation,
            swapViewModel = navigation.swapViewModel()
        )
    }
}

/** Transaction settings of the SwapConfirmViewModel owned by [confirmPage]. */
internal class SwapTransactionSettingsPage(private val confirmPage: KClass<out HSPage>) : HSPage() {
    @Composable
    override fun GetContent(navigation: HSNavigation) {
        SwapTransactionSettingsScreen(navigation = navigation, confirmPage = confirmPage)
    }
}

private class PayCoreVerificationPage(
    private val networkType: PayCoreTicker,
    private val walletAddress: String,
) : HSPage() {
    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel = navigation.swapViewModel()
        PayCoreVerificationScreen(
            networkType = networkType,
            walletAddress = walletAddress,
            onClose = navigation::navigateUpSafely,
            onComplete = {
                viewModel.onActionCompleted()
                navigation.navigateUpSafely()
            }
        )
    }
}

internal class PayCorePaymentPage(val input: Input) : HSPage() {
    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel = navigation.swapViewModel()
        val tokenIn = viewModel.uiState.tokenIn ?: run {
            LaunchedEffect(Unit) { navigation.navigateUpFrom(this@PayCorePaymentPage) }
            return
        }
        val tokenOut = viewModel.uiState.tokenOut ?: run {
            LaunchedEffect(Unit) { navigation.navigateUpFrom(this@PayCorePaymentPage) }
            return
        }
        val paymentParams = remember(this, tokenOut) {
            input.toPaymentParams(
                addressOut = tryOrNull {
                    getKoinInstance<WalletUseCase>().getReceiveAddress(tokenOut)
                }.orEmpty(),
            )
        }
        val paymentViewModel = koinViewModel<PayCorePaymentViewModel>(
            key = listOf(
                input.amountIn,
                input.amountOut,
                input.direction,
                input.requestedAmountOut,
                input.networkType,
            ).joinToString("|"),
        ) { parametersOf(paymentParams) }
        PayCorePaymentScreen(
            uiState = paymentViewModel.uiState,
            displayParams = PayCorePaymentDisplayParams(
                amountIn = paymentViewModel.amountIn,
                amountOut = paymentViewModel.amountOut,
                serviceFee = input.serviceFee.toBigDecimal(),
                networkType = paymentViewModel.networkType,
                tokenIn = tokenIn,
                tokenOut = tokenOut,
                currency = viewModel.uiState.currency,
            ),
            onConfirm = paymentViewModel::onConfirm,
            onOpenWebView = paymentViewModel::onWebViewOpened,
            onCompleteWebView = paymentViewModel::onWebViewCompleted,
            onCloseWebView = paymentViewModel::onWebViewClosed,
            onClose = navigation::navigateUpSafely
        )
    }

    @Parcelize
    data class Input(
        val amountIn: String,
        val amountOut: String,
        val serviceFee: String,
        val networkType: PayCoreTicker,
        val tokenInUid: String,
        val tokenOutUid: String,
        val blockchainTypeIn: String,
        val blockchainTypeOut: String,
        val direction: SwapAmountDirection,
        val requestedAmountOut: String?,
    ) : Parcelable
}

/** Listed in r8-critical-classes.txt; navigation no longer resolves it by name. */
@Keep
private enum class SwapCoinDirection { From, To }

private enum class SwapInputFocus { None, AmountIn, FiatAmountIn, AmountOut, FiatAmountOut }

@Composable
fun SwapScreen(navigation: HSNavigation, tokenIn: Token?, tokenOut: Token?) {
    val viewModel = viewModel<SwapViewModel>(
        factory = SwapViewModel.Factory(tokenIn, tokenOut)
    )
    SwapMainScreen(
        navigation = navigation,
        viewModel = viewModel
    )
}

internal fun buildNextPage(uiState: SwapUiState): HSPage {
    return buildPayCorePaymentPage(uiState)
        ?: if (uiState.multiSwapRoute != null) SwapRouteInfoPage() else SwapConfirmPage()
}

internal fun buildPayCorePaymentPage(uiState: SwapUiState): PayCorePaymentPage? {
    val quote = uiState.quote
    val tokenIn = uiState.tokenIn
    if (quote == null || tokenIn == null) return null
    val tokenOut = uiState.tokenOut
    val amountIn = uiState.amountIn
    if (tokenOut == null || amountIn == null) return null

    val isPayCoreRubPayment = quote.provider.id == "paycore" && PayCoreAssets.isRub(tokenIn)
    if (!isPayCoreRubPayment) return null

    val networkType = tokenOut.toTicker() ?: return null
    return PayCorePaymentPage(
        PayCorePaymentPage.Input(
            amountIn = amountIn.toPlainString(),
            amountOut = quote.amountOut.toPlainString(),
            serviceFee = payCoreQuoteServiceFee(quote).toPlainString(),
            networkType = networkType,
            tokenInUid = tokenIn.coin.uid,
            tokenOutUid = tokenOut.coin.uid,
            blockchainTypeIn = tokenIn.blockchainType.uid,
            blockchainTypeOut = tokenOut.blockchainType.uid,
            direction = uiState.direction,
            requestedAmountOut = uiState.requestedAmountOut?.toPlainString(),
        )
    )
}

internal fun PayCorePaymentPage.Input.toPaymentParams(addressOut: String) = PayCorePaymentParams(
    amountIn = amountIn.toBigDecimal(),
    amountOut = amountOut.toBigDecimal(),
    networkType = networkType,
    tokenInUid = tokenInUid,
    tokenOutUid = tokenOutUid,
    blockchainTypeIn = blockchainTypeIn,
    blockchainTypeOut = blockchainTypeOut,
    addressOut = addressOut,
    direction = direction,
    requestedAmountOut = requestedAmountOut?.toBigDecimal(),
)

private fun payCoreQuoteServiceFee(quote: SwapProviderQuote): BigDecimal {
    return (quote.swapQuote as? PayCoreQuote)?.serviceFee ?: BigDecimal.ZERO
}

private fun SwapUiState.otherToken(direction: SwapCoinDirection): Token? = when (direction) {
    SwapCoinDirection.From -> tokenOut
    SwapCoinDirection.To -> tokenIn
}

private fun SwapCoinDirection.titleResId(): Int = when (this) {
    SwapCoinDirection.From -> R.string.Swap_YouPay
    SwapCoinDirection.To -> R.string.Swap_YouGet
}

private fun buildMultiSwapLeg1Info(viewModel: SwapViewModel): MultiSwapLegInfo? {
    val route = viewModel.uiState.multiSwapRoute ?: return null
    val uiState = viewModel.uiState
    val tokenIn = uiState.tokenIn ?: return null
    val tokenOut = uiState.tokenOut ?: return null
    val amountIn = uiState.amountIn ?: return null
    val leg2Provider = route.selectedLeg2Quote.provider
    return MultiSwapLegInfo.Leg1(
        coinUidIn = tokenIn.coin.uid,
        blockchainTypeIn = tokenIn.blockchainType.uid,
        amountIn = amountIn,
        coinUidIntermediate = route.intermediateCoin.coin.uid,
        blockchainTypeIntermediate = route.intermediateCoin.blockchainType.uid,
        coinUidOut = tokenOut.coin.uid,
        blockchainTypeOut = tokenOut.blockchainType.uid,
        leg1ProviderId = route.selectedLeg1Quote.provider.id,
        leg2ProviderId = leg2Provider.id,
        leg2IsOffChain = leg2Provider.isOffChain,
        expectedAmountOut = route.selectedLeg2Quote.amountOut,
    )
}

@Composable
private fun SwapMainScreen(
    navigation: HSNavigation,
    viewModel: SwapViewModel
) {
    val uiState = viewModel.uiState
    val view = LocalView.current
    val manageWalletsFactory = remember { ManageWalletsModule.Factory() }
    val restoreSettingsViewModel =
        viewModel<RestoreSettingsViewModel>(factory = manageWalletsFactory)
    val manageWalletsViewModel = viewModel<ManageWalletsViewModel>(factory = manageWalletsFactory)

    navigation.openRestoreSettingsDialog(
        token = restoreSettingsViewModel.openTokenConfigure,
        restoreSettingsViewModel = restoreSettingsViewModel
    )

    LaunchedEffect(manageWalletsViewModel.errorMsg) {
        manageWalletsViewModel.errorMsg?.let {
            HudHelper.showErrorMessage(view, it)
        }
    }

    val openSettings = remember(navigation) {
        { navigation.slideFromRight(SwapSettingsPage()) }
    }
    val openVerification = remember(navigation) {
        {
            val tokenIn = viewModel.uiState.tokenIn
            val tokenOut = viewModel.uiState.tokenOut
            val usdtToken = if (tokenIn != null && PayCoreAssets.isRub(tokenIn)) tokenOut else tokenIn
            val networkType = usdtToken?.toTicker()
            val walletAddress = usdtToken?.let { token ->
                tryOrNull { getKoinInstance<WalletUseCase>().getReceiveAddress(token) }
            }
            if (networkType != null && !walletAddress.isNullOrBlank()) {
                navigation.slideFromRight(PayCoreVerificationPage(networkType, walletAddress))
            }
        }
    }
    val controller = remember(viewModel, navigation, manageWalletsViewModel) {
        SwapScreenController(
            input = SwapInputController(
                enterAmount = viewModel::onEnterAmount,
                enterAmountOut = viewModel::onEnterAmountOut,
                enterFiatAmount = viewModel::onEnterFiatAmount,
                enterFiatAmountOut = viewModel::onEnterFiatAmountOut,
                enterAmountPercentage = viewModel::onEnterAmountPercentage,
            ),
            navigation = SwapNavigationController(
                close = navigation::navigateUpSafely,
                selectCoinFrom = { navigation.slideFromBottom(SwapSelectCoinPage(SwapCoinDirection.From)) },
                selectCoinTo = { navigation.slideFromBottom(SwapSelectCoinPage(SwapCoinDirection.To)) },
                openProvider = { navigation.slideFromBottom(SwapSelectProviderPage()) },
                openSettings = openSettings,
            ),
            operations = SwapOperationsController(
                timeRemainingProgress = { viewModel.timeRemainingProgress },
                switchPairs = viewModel::onSwitchPairs,
                refreshQuote = viewModel::reQuote,
                proceed = { navigation.slideFromRight(buildNextPage(viewModel.uiState)) },
                toggleBalance = viewModel::toggleHideBalance,
                executeAction = { action, actionNavigation ->
                    viewModel.onActionStarted()
                    when (action) {
                        is ActionCreate -> {
                            action.tokensToAdd.forEach(manageWalletsViewModel::enable)
                            if (manageWalletsViewModel.showScanToAddButton) {
                                manageWalletsViewModel.requestScanToAddTokens(false)
                            }
                            viewModel.createMissingTokens(action.tokensToAdd)
                        }
                        is PayCoreSelectBankAction -> openSettings()
                        is PayCoreVerificationAction -> openVerification()
                        else -> action.execute(actionNavigation, viewModel::onActionCompleted)
                    }
                },
            ),
        )
    }

    SwapScreenInner(
        uiState = uiState,
        controller = controller,
        navigation = navigation,
    )
}

private class SwapScreenController(
    private val input: SwapInputController,
    private val navigation: SwapNavigationController,
    private val operations: SwapOperationsController,
) {
    val timeRemainingProgress: Float?
        get() = operations.timeRemainingProgress()

    fun close() = navigation.close()
    fun selectCoinFrom() = navigation.selectCoinFrom()
    fun selectCoinTo() = navigation.selectCoinTo()
    fun switchPairs() = operations.switchPairs()
    fun enterAmount(amount: BigDecimal?) = input.enterAmount(amount)
    fun enterAmountOut(amount: BigDecimal?) = input.enterAmountOut(amount)
    fun enterFiatAmount(amount: BigDecimal?) = input.enterFiatAmount(amount)
    fun enterFiatAmountOut(amount: BigDecimal?) = input.enterFiatAmountOut(amount)
    fun enterAmountPercentage(percentage: Int) = input.enterAmountPercentage(percentage)
    fun openProvider() = navigation.openProvider()
    fun openSettings() = navigation.openSettings()
    val refreshQuote: () -> Unit
        get() = operations.refreshQuote
    fun proceed() = operations.proceed()
    fun toggleBalance() = operations.toggleBalance()

    fun executeAction(action: ISwapProviderAction, navigation: HSNavigation) {
        operations.executeAction(action, navigation)
    }
}

private data class SwapInputController(
    val enterAmount: (BigDecimal?) -> Unit,
    val enterAmountOut: (BigDecimal?) -> Unit,
    val enterFiatAmount: (BigDecimal?) -> Unit,
    val enterFiatAmountOut: (BigDecimal?) -> Unit,
    val enterAmountPercentage: (Int) -> Unit,
)

private data class SwapNavigationController(
    val close: () -> Unit,
    val selectCoinFrom: () -> Unit,
    val selectCoinTo: () -> Unit,
    val openProvider: () -> Unit,
    val openSettings: () -> Unit,
)

private data class SwapOperationsController(
    val timeRemainingProgress: () -> Float?,
    val switchPairs: () -> Unit,
    val refreshQuote: () -> Unit,
    val proceed: () -> Unit,
    val toggleBalance: () -> Unit,
    val executeAction: (ISwapProviderAction, HSNavigation) -> Unit,
)

@Composable
private fun SwapScreenInner(
    uiState: SwapUiState,
    controller: SwapScreenController,
    navigation: HSNavigation,
) {
    LifecycleResumeEffect(uiState.timeout) {
        if (uiState.timeout) {
            controller.refreshQuote()
        }

        onPauseOrDispose { }
    }

    Scaffold(
        topBar = { SwapAppBar(uiState, controller) },
        containerColor = ComposeAppTheme.colors.tyler,
    ) { contentPadding ->
        val keyboardState by observeKeyboardState()
        var inputFocus by remember { mutableStateOf(SwapInputFocus.None) }
        Box(modifier = Modifier.fillMaxSize()) {
            SwapScreenContent(
                uiState = uiState,
                controller = controller,
                navigation = navigation,
                modifier = Modifier.padding(contentPadding),
                onFocusChange = { focus, state ->
                    inputFocus = if (state.isFocused) focus else SwapInputFocus.None
                },
            )
            SwapSuggestions(
                uiState = uiState,
                controller = controller,
                inputFocus = inputFocus,
                keyboardOpen = keyboardState == Keyboard.Opened,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun SwapAppBar(uiState: SwapUiState, controller: SwapScreenController) {
    AppBar(
        title = stringResource(R.string.Swap),
        navigationIcon = { HsBackButton(onClick = controller::close) },
        menuItems = buildList {
            controller.timeRemainingProgress?.let { add(MenuItemTimeoutIndicator(it)) }
            if (uiState.quote?.swapQuote?.settings?.isNotEmpty() == true) {
                add(
                    MenuItem(
                        title = TranslatableString.ResString(R.string.SwapSettings_Title),
                        icon = R.drawable.ic_manage_2_24,
                        onClick = controller::openSettings,
                    )
                )
            }
        },
    )
}

@Composable
private fun SwapScreenContent(
    uiState: SwapUiState,
    controller: SwapScreenController,
    navigation: HSNavigation,
    modifier: Modifier = Modifier,
    onFocusChange: (SwapInputFocus, FocusState) -> Unit = { _, _ -> },
) {
    Column(
        modifier = modifier
            .imePadding()
            .verticalScroll(rememberScrollState())
    ) {
        VSpacer(height = 12.dp)
        SwapInput(uiState, controller, onFocusChange)
        VSpacer(height = 12.dp)
        SwapStepButton(uiState, controller, navigation)
        VSpacer(height = 12.dp)
        SwapFeeInfo(uiState, controller)
        VSpacer(height = 12.dp)
        SwapQuoteInfo(uiState, controller, navigation)
        SwapWarnings(uiState)
        VSpacer(height = 32.dp)
    }
}

@Composable
private fun SwapStepButton(
    uiState: SwapUiState,
    controller: SwapScreenController,
    navigation: HSNavigation,
) {
    when (val currentStep = uiState.currentStep) {
        is SwapStep.InputRequired -> DisabledSwapButton(inputRequiredTitle(currentStep.inputType))
        SwapStep.Quoting -> DisabledSwapButton(stringResource(R.string.Swap_Quoting), loading = true)
        is SwapStep.Error -> DisabledSwapButton(swapErrorText(currentStep.error))
        is SwapStep.ActionRequired -> {
            val action = currentStep.action
            ButtonPrimaryDefault(
                modifier = swapButtonModifier(),
                title = if (action.inProgress) action.getTitleInProgress() else action.getTitle(),
                enabled = !action.inProgress,
                onClick = { controller.executeAction(action, navigation) },
            )
        }
        SwapStep.Proceed -> ButtonPrimaryYellow(
            modifier = swapButtonModifier(),
            title = stringResource(R.string.Swap_Proceed),
            enabled = !uiState.insufficientFeeBalance,
            onClick = controller::proceed,
        )
    }
}

@Composable
private fun DisabledSwapButton(title: String, loading: Boolean = false) {
    ButtonPrimaryYellow(
        modifier = swapButtonModifier(),
        title = title,
        enabled = false,
        loadingIndicator = loading,
        onClick = {},
    )
}

private fun swapButtonModifier(): Modifier =
    Modifier.padding(horizontal = 16.dp).fillMaxWidth()

@Composable
private fun inputRequiredTitle(inputType: InputType): String = stringResource(
    when (inputType) {
        InputType.TokenIn -> R.string.Swap_SelectTokenIn
        InputType.TokenOut -> R.string.Swap_SelectTokenOut
        InputType.Amount -> R.string.Swap_EnterAmount
    }
)

@Composable
private fun swapErrorText(error: Throwable): String = when (error) {
    SwapError.InsufficientBalanceFrom -> stringResource(R.string.Swap_ErrorInsufficientBalance)
    is NoSupportedSwapProvider -> stringResource(R.string.Swap_ErrorNoProviders)
    is NoEnabledSwapProvider -> stringResource(R.string.swap_no_enabled_providers)
    is NoExactOutSwapProvider -> stringResource(R.string.Swap_ErrorNoQuote)
    is SwapRouteNotFound -> stringResource(R.string.Swap_ErrorNoQuote)
    is SwapDepositTooSmall -> stringResource(R.string.swap_out_of_min_amount, error.minValue.toPlainString())
    is SwapAmountOutOfRange -> stringResource(R.string.swap_no_providers_for_this_amount)
    is PriceImpactTooHigh -> stringResource(R.string.Swap_ErrorHighPriceImpact)
    is UnknownHostException -> stringResource(R.string.Hud_Text_NoInternet)
    is WalletSyncing -> stringResource(R.string.Swap_ErrorWalletSyncing)
    is WalletNotSynced -> stringResource(R.string.Swap_ErrorWalletNotSynced)
    else -> error.message ?: error.javaClass.simpleName
}

@Composable
private fun SwapFeeInfo(uiState: SwapUiState, controller: SwapScreenController) {
    val feeToken = uiState.feeToken
    val networkFee = uiState.networkFee
    FeeInfoSection(
        tokenIn = uiState.tokenIn,
        displayBalance = uiState.displayBalance,
        balanceHidden = uiState.balanceHidden,
        feeToken = feeToken,
        feeCoinBalance = uiState.feeCoinBalance,
        feePrimary = if (feeToken != null && networkFee != null) {
            CoinValue(feeToken, networkFee).getFormattedFull()
        } else {
            "---"
        },
        feeSecondary = uiState.networkFeeFiatAmount?.let {
            App.numberFormatter.formatFiatFull(it, uiState.currency.symbol)
        }.orEmpty(),
        insufficientFeeBalance = uiState.insufficientFeeBalance,
        onBalanceClicked = controller::toggleBalance,
        feeTitle = stringResource(R.string.estimated_fee),
    )
}

@Composable
private fun SwapQuoteInfo(
    uiState: SwapUiState,
    controller: SwapScreenController,
    navigation: HSNavigation,
) {
    val quote = uiState.quote ?: return
    CardsSwapInfo {
        ProviderField(quote.provider, quote.estimationTime, controller::openProvider)
        val finalTokenOut = uiState.tokenOut ?: quote.tokenOut
        val finalAmountOut = uiState.multiSwapRoute?.selectedLeg2Quote?.amountOut ?: quote.amountOut
        PriceField(quote.tokenIn, finalTokenOut, quote.amountIn, finalAmountOut)
        PriceImpactField(uiState.priceImpact, uiState.priceImpactLevel)
        quote.fields.forEach { it.GetContent(navigation, true) }
    }
}

@Composable
private fun SwapWarnings(uiState: SwapUiState) {
    Column {
        uiState.warningMessage?.let {
            VSpacer(height = 12.dp)
            TextImportantWarning(
                modifier = Modifier.padding(horizontal = 16.dp),
                text = it.getString(),
                icon = R.drawable.ic_attention_20,
            )
        }
        if (uiState.quoteCautions.isNotEmpty()) {
            Cautions(uiState.quoteCautions.map { it.toCautionViewItem() })
        }
        if (uiState.direction == SwapAmountDirection.Out &&
            uiState.amountOutAccuracy == SwapAmountAccuracy.Estimated
        ) {
            VSpacer(height = 12.dp)
            TextImportantWarning(
                modifier = Modifier.padding(horizontal = 16.dp),
                text = stringResource(R.string.swap_estimated_amount_warning),
                icon = R.drawable.ic_attention_20,
            )
        }
        SwapStepWarning(uiState)
    }
}

@Composable
private fun SwapStepWarning(uiState: SwapUiState) {
    val action = (uiState.currentStep as? SwapStep.ActionRequired)?.action
    if (uiState.error is PriceImpactTooHigh) {
        VSpacer(height = 12.dp)
        TextImportantError(
            modifier = Modifier.padding(horizontal = 16.dp),
            icon = R.drawable.ic_attention_20,
            title = stringResource(R.string.Swap_PriceImpact),
            text = stringResource(R.string.Swap_PriceImpactTooHigh, uiState.error.providerTitle.orEmpty()),
        )
    } else {
        action?.getDescription()?.let {
            VSpacer(height = 12.dp)
            TextImportantWarning(modifier = Modifier.padding(horizontal = 16.dp), text = it)
        }
    }
}

@Composable
private fun SwapSuggestions(
    uiState: SwapUiState,
    controller: SwapScreenController,
    inputFocus: SwapInputFocus,
    keyboardOpen: Boolean,
    modifier: Modifier = Modifier,
) {
    if (inputFocus == SwapInputFocus.None || !keyboardOpen) return
    val focusManager = LocalFocusManager.current
    val hasBalance = uiState.availableBalance?.signum() == 1
    val percentagesEnabled = inputFocus == SwapInputFocus.AmountIn
    SuggestionsBar(
        modifier = modifier.imePadding(),
        onDelete = { inputFocus.clear(controller) },
        onSelect = {
            focusManager.clearFocus()
            controller.enterAmountPercentage(it)
        },
        percents = if (percentagesEnabled) listOf(25, 50, 75, 100) else emptyList(),
        selectEnabled = hasBalance && percentagesEnabled,
        deleteEnabled = inputFocus.hasValue(uiState),
    )
}

private fun SwapInputFocus.clear(controller: SwapScreenController) {
    when (this) {
        SwapInputFocus.None -> Unit
        SwapInputFocus.AmountIn -> controller.enterAmount(null)
        SwapInputFocus.FiatAmountIn -> controller.enterFiatAmount(null)
        SwapInputFocus.AmountOut -> controller.enterAmountOut(null)
        SwapInputFocus.FiatAmountOut -> controller.enterFiatAmountOut(null)
    }
}

private fun SwapInputFocus.hasValue(uiState: SwapUiState): Boolean = when (this) {
    SwapInputFocus.None -> false
    SwapInputFocus.AmountIn -> uiState.amountIn != null
    SwapInputFocus.FiatAmountIn -> uiState.fiatAmountIn != null
    SwapInputFocus.AmountOut -> uiState.displayAmountOut != null
    SwapInputFocus.FiatAmountOut -> uiState.fiatAmountOut != null
}

@Composable
fun PriceImpactField(
    priceImpact: BigDecimal?,
    priceImpactLevel: PriceImpactLevel?,
    borderTop: Boolean = true
) {
    if (priceImpact == null || priceImpactLevel == null) return

    val infoTitle = stringResource(id = R.string.SwapInfo_PriceImpactTitle)
    val infoText = stringResource(id = R.string.SwapInfo_PriceImpactDescription)
    var showInfoDialog by remember { mutableStateOf(false) }

    QuoteInfoRow(
        borderTop = borderTop,
        title = {
            subhead2_grey(text = stringResource(R.string.Swap_PriceImpact))

            Image(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .clickable(
                        onClick = { showInfoDialog = true },
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ),
                painter = painterResource(id = R.drawable.ic_info_20),
                contentDescription = ""
            )
        },
        value = {
            Text(
                text = stringResource(
                    R.string.Swap_Percent,
                    priceImpact.toPlainString()
                ),
                style = ComposeAppTheme.typography.subhead2,
                color = getPriceImpactColor(priceImpactLevel),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    )

    if (showInfoDialog) {
        InfoBottomSheet(
            title = infoTitle,
            text = infoText,
            onDismiss = { showInfoDialog = false }
        )
    }
}

@Composable
private fun ProviderField(
    swapProvider: IMultiSwapProvider,
    estimationTime: Long?,
    onClickProvider: () -> Unit,
) {
    HSRow(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClickProvider,
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        borderBottom = true,
    ) {
        Image(
            modifier = Modifier.size(32.dp),
            painter = painterResource(swapProvider.icon),
            contentDescription = null
        )
        HSpacer(width = 8.dp)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            subhead1_leah(text = swapProvider.title)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                estimationTime?.let { EstimationTimeBadge(seconds = it) }
                ProviderRiskBadge(riskType = swapProvider.riskType)
            }
        }
        HSpacer(width = 8.dp)
        Icon(
            painter = painterResource(R.drawable.ic_arrow_right),
            contentDescription = null,
            tint = ComposeAppTheme.colors.grey
        )
    }
}

@Composable
fun PriceField(
    tokenIn: Token,
    tokenOut: Token,
    amountIn: BigDecimal,
    amountOut: BigDecimal,
    borderTop: Boolean = false,
) {
    if (amountIn <= BigDecimal.ZERO || amountOut <= BigDecimal.ZERO) return

    var showRegularPrice by remember { mutableStateOf(true) }
    val swapPriceUIHelper = SwapPriceUIHelper(tokenIn, tokenOut, amountIn, amountOut)

    QuoteInfoRow(
        borderTop = borderTop,
        title = {
            subhead2_grey(text = stringResource(R.string.Swap_Price))
        },
        value = {
            Row(
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            showRegularPrice = !showRegularPrice
                        }
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                subhead2_leah(
                    text = if (showRegularPrice) {
                        swapPriceUIHelper.priceStr
                    } else {
                        swapPriceUIHelper.priceInvStr
                    }
                )
                HSpacer(width = 8.dp)
                Icon(
                    painter = painterResource(id = R.drawable.ic_arrow_swap3_20),
                    contentDescription = "invert price",
                    tint = ComposeAppTheme.colors.grey
                )
            }
        }
    )
}

@Composable
private fun SwapInput(
    uiState: SwapUiState,
    controller: SwapScreenController,
    onFocusChange: (SwapInputFocus, FocusState) -> Unit,
) {
    Box(
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(ComposeAppTheme.colors.lawrence)
        ) {
            SwapCoinInputIn(
                uiState = uiState,
                controller = controller,
                onFocusChange = onFocusChange,
            )
            SwapCoinInputTo(
                state = SwapOutputInputState.from(uiState),
                onAmountChange = controller::enterAmountOut,
                onFiatAmountChange = controller::enterFiatAmountOut,
                onSelectCoin = controller::selectCoinTo,
                onFocusChange = onFocusChange,
            )
        }
        HorizontalDivider(
            modifier = Modifier.align(Alignment.Center),
            thickness = 1.dp,
            color = ComposeAppTheme.colors.steel20
        )
        SwapDirectionIndicator(
            modifier = Modifier.align(Alignment.Center),
            intermediateToken = uiState.multiSwapRoute?.intermediateCoin,
            onClick = controller::switchPairs
        )
    }
}

@Composable
private fun SwapCoinInputIn(
    uiState: SwapUiState,
    controller: SwapScreenController,
    onFocusChange: (SwapInputFocus, FocusState) -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            AmountInput(
                value = uiState.amountIn,
                accessibilityLabel = stringResource(R.string.Swap_YouPay),
                onValueChange = controller::enterAmount,
                onFocusChange = { onFocusChange(SwapInputFocus.AmountIn, it) },
            )
            VSpacer(height = 3.dp)
            FiatAmountInput(
                value = uiState.fiatAmountIn,
                currency = uiState.currency,
                onValueChange = controller::enterFiatAmount,
                enabled = uiState.fiatAmountInInputEnabled,
                onFocusChange = { onFocusChange(SwapInputFocus.FiatAmountIn, it) },
            )
        }
        HSpacer(width = 8.dp)
        CoinSelector(uiState.tokenIn, controller::selectCoinFrom)
    }
}

@Composable
private fun SwapCoinInputTo(
    state: SwapOutputInputState,
    onAmountChange: (BigDecimal?) -> Unit,
    onFiatAmountChange: (BigDecimal?) -> Unit,
    onSelectCoin: () -> Unit,
    onFocusChange: (SwapInputFocus, FocusState) -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            AmountInput(
                value = state.amount,
                accessibilityLabel = stringResource(R.string.Swap_YouGet),
                onValueChange = onAmountChange,
                onFocusChange = { onFocusChange(SwapInputFocus.AmountOut, it) },
            )
            VSpacer(height = 3.dp)
            Row {
                FiatAmountInput(
                    value = state.fiatAmount,
                    currency = state.currency,
                    onValueChange = onFiatAmountChange,
                    enabled = state.fiatAmountInputEnabled,
                    onFocusChange = { onFocusChange(SwapInputFocus.FiatAmountOut, it) },
                    modifier = Modifier.weight(1f, fill = false),
                    fillWidth = state.fiatPriceImpact == null,
                )
                state.fiatPriceImpact?.let { diff ->
                    HSpacer(width = 4.dp)
                    Text(
                        text = stringResource(
                            R.string.Swap_FiatPriceImpact,
                            diff.toPlainString()
                        ),
                        style = ComposeAppTheme.typography.body,
                        color = getPriceImpactColor(state.fiatPriceImpactLevel),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        HSpacer(width = 8.dp)
        CoinSelector(state.token, onSelectCoin)
    }
}

private data class SwapOutputInputState(
    val amount: BigDecimal?,
    val fiatAmount: BigDecimal?,
    val fiatPriceImpact: BigDecimal?,
    val currency: Currency,
    val token: Token?,
    val fiatAmountInputEnabled: Boolean,
    val fiatPriceImpactLevel: PriceImpactLevel?,
) {
    companion object {
        fun from(uiState: SwapUiState) = SwapOutputInputState(
            amount = uiState.displayAmountOut,
            fiatAmount = uiState.fiatAmountOut,
            fiatPriceImpact = uiState.fiatPriceImpact,
            currency = uiState.currency,
            token = uiState.tokenOut,
            fiatAmountInputEnabled = uiState.fiatAmountOutInputEnabled,
            fiatPriceImpactLevel = uiState.fiatPriceImpactLevel,
        )
    }
}

@Preview(name = "Exact in", showBackground = true)
@Composable
internal fun SwapOutputInputExactInPreview() {
    SwapOutputInputPreview(
        amount = BigDecimal("0.12345678"),
        fiatAmount = BigDecimal("20"),
        fiatPriceImpact = BigDecimal("-1.62"),
    )
}

@Preview(name = "Exact out", showBackground = true)
@Composable
internal fun SwapOutputInputExactOutPreview() {
    SwapOutputInputPreview(
        amount = BigDecimal("100"),
        fiatAmount = BigDecimal("123456789.12"),
        fiatPriceImpact = BigDecimal("1.23"),
    )
}

@Preview(name = "No price impact", showBackground = true)
@Composable
internal fun SwapOutputInputNoPriceImpactPreview() {
    SwapOutputInputPreview(
        amount = BigDecimal("100"),
        fiatAmount = null,
        fiatPriceImpact = null,
    )
}

@Composable
private fun SwapOutputInputPreview(
    amount: BigDecimal,
    fiatAmount: BigDecimal?,
    fiatPriceImpact: BigDecimal?,
) {
    ComposeAppTheme {
        SwapCoinInputTo(
            state = SwapOutputInputState(
                amount = amount,
                fiatAmount = fiatAmount,
                fiatPriceImpact = fiatPriceImpact,
                currency = Currency("usd", "$", 6, 0),
                token = null,
                fiatAmountInputEnabled = true,
                fiatPriceImpactLevel = PriceImpactLevel.Normal,
            ),
            onAmountChange = {},
            onFiatAmountChange = {},
            onSelectCoin = {},
            onFocusChange = { _, _ -> },
        )
    }
}

@Composable
private fun CoinSelector(
    token: Token?,
    onClickCoin: () -> Unit,
) {
    Selector(
        icon = {
            CoinImage(
                token = token,
                modifier = Modifier.size(32.dp)
            )
        },
        text = {
            if (token != null) {
                Column {
                    subhead1_leah(text = token.coin.code)
                    VSpacer(height = 1.dp)
                    micro_grey(
                        text = token.badge ?: stringResource(id = R.string.CoinPlatforms_Native)
                    )
                }
            } else {
                subhead1_jacob(text = stringResource(R.string.Swap_TokenSelectorTitle))
            }
        },
        onClickSelect = onClickCoin
    )
}

@Composable
private fun FiatAmountInput(
    value: BigDecimal?,
    currency: Currency,
    onValueChange: (BigDecimal?) -> Unit,
    enabled: Boolean,
    onFocusChange: (FocusState) -> Unit,
    modifier: Modifier = Modifier,
    fillWidth: Boolean = true,
) {
    var text by remember(value) {
        mutableStateOf(value?.toPlainString() ?: "")
    }
    val textStyle = ColoredTextStyle(
        color = ComposeAppTheme.colors.grey,
        textStyle = ComposeAppTheme.typography.body
    )
    val inputModifier = if (fillWidth) {
        Modifier.fillMaxWidth()
    } else {
        val textWidth = with(LocalDensity.current) {
            rememberTextMeasurer()
                .measure(text.ifEmpty { "0" }, textStyle, maxLines = 1)
                .size.width
                .toDp()
        }
        Modifier.width(textWidth)
    }
    Row(modifier = modifier) {
        body_grey(text = currency.symbol)
        BasicTextField(
            modifier = inputModifier.onFocusChanged(onFocusChange),
            value = text,
            onValueChange = onTextChange@{ updatedText ->
                val amount = if (updatedText.isBlank()) {
                    null
                } else {
                    parseAmount(updatedText) ?: return@onTextChange
                }
                text = updatedText
                onValueChange(amount)
            },
            enabled = enabled,
            textStyle = textStyle,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal
            ),
            cursorBrush = SolidColor(ComposeAppTheme.colors.jacob),
            decorationBox = { innerTextField ->
                if (text.isEmpty()) {
                    body_grey(text = "0")
                }
                innerTextField()
            },
        )
    }
}

@Composable
private fun Selector(
    icon: @Composable() (RowScope.() -> Unit),
    text: @Composable() (RowScope.() -> Unit),
    onClickSelect: () -> Unit,
) {
    Row(
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClickSelect,
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon.invoke(this)
        HSpacer(width = 8.dp)
        text.invoke(this)
        HSpacer(width = 8.dp)
        Icon(
            painter = painterResource(R.drawable.ic_arrow_big_down_20),
            contentDescription = "",
            tint = ComposeAppTheme.colors.grey
        )
    }
}

@Composable
private fun AmountInput(
    value: BigDecimal?,
    accessibilityLabel: String,
    onValueChange: (BigDecimal?) -> Unit,
    onFocusChange: (FocusState) -> Unit,
) {
    var amount by rememberSaveable { mutableStateOf(value) }
    var textFieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(text = amount?.toPlainString() ?: ""))
    }
    LaunchedEffect(value) {
        if (value?.stripTrailingZeros() != amount?.stripTrailingZeros()) {
            amount = value
            textFieldValue = TextFieldValue(text = amount?.toPlainString() ?: "")
        }
    }
    var setCursorToEndOnFocused by remember { mutableStateOf(false) }
    BasicTextField(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = accessibilityLabel }
            .onFocusChanged {
                onFocusChange(it)
                setCursorToEndOnFocused = it.isFocused
                if (!it.isFocused) {
                    textFieldValue = textFieldValue.copy(selection = TextRange.Zero)
                }
            },
        value = textFieldValue,
        onValueChange = { newValue ->
            val text = newValue.text
            val parsedAmount = parseAmount(text)
            val negative = parsedAmount?.let { it < BigDecimal.ZERO } == true
            amount = parsedAmount?.takeUnless { negative }

            if (negative) {
                textFieldValue = TextFieldValue()
            } else if (!setCursorToEndOnFocused) {
                textFieldValue = newValue
            } else {
                textFieldValue = newValue.copy(selection = TextRange(text.length))
                setCursorToEndOnFocused = false
            }

            onValueChange(amount)
        },
        textStyle = ColoredTextStyle(
            color = ComposeAppTheme.colors.leah,
            textStyle = ComposeAppTheme.typography.headline1
        ),
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal
        ),
        cursorBrush = SolidColor(ComposeAppTheme.colors.jacob),
        decorationBox = { innerTextField ->
            if (textFieldValue.text.isEmpty()) {
                headline1_grey(text = "0")
            }
            innerTextField()
        },
    )
}

private fun parseAmount(value: String): BigDecimal? =
    value.takeUnless(String::isBlank)?.let { tryOrNull { it.toBigDecimalOrNullExt() } }

@Composable
fun getPriceImpactColor(priceImpactLevel: PriceImpactLevel?): Color {
    return when (priceImpactLevel) {
        PriceImpactLevel.Warning -> ComposeAppTheme.colors.lucian
        PriceImpactLevel.Good -> ComposeAppTheme.colors.remus
        else -> ComposeAppTheme.colors.grey
    }
}
