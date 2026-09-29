package cash.p.terminal.modules.send.beam

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import cash.p.terminal.R
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.core.utils.AddressUriParser
import cash.p.terminal.entities.Address
import cash.p.terminal.modules.address.AddressViewModel
import cash.p.terminal.modules.address.AmountUnique
import cash.p.terminal.modules.address.HSAddressInput
import cash.p.terminal.modules.amount.AmountInputType
import cash.p.terminal.modules.amount.HSAmountInput
import cash.p.terminal.modules.fee.FeeInfoSection
import cash.p.terminal.modules.send.SendConfirmationScreen
import cash.p.terminal.modules.send.SendFragment.ProceedActionData
import cash.p.terminal.modules.send.SendScreen
import cash.p.terminal.modules.send.SendSuggestionsBar
import cash.p.terminal.modules.send.fee.feePrimaryText
import cash.p.terminal.modules.send.fee.feeSecondaryText
import cash.p.terminal.modules.send.offline.OfflineSignActionCell
import cash.p.terminal.modules.send.offline.OfflineSignRouteState
import cash.p.terminal.modules.send.offline.offlineSignRoute
import cash.p.terminal.modules.send.offline.offlineTransactionTransferRoute
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.navigation.popBackStackSafely
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.HudHelper
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.entities.CurrencyValue
import java.math.BigDecimal

private const val BeamSendFormPage = "beam_send_form"
private const val BeamOfflineSignPage = "beam_offline_sign"
private const val BeamOfflineTransferPage = "beam_offline_transfer"

// No recipient-check step: AddressCheckManager refuses every screening type for BEAM on purpose
// ("BEAM receiver tokens are opaque payment data and must not reach address screening providers",
// AddressCheckManager.kt:58), so the security report would always be empty. The switch is gone too —
// it writes the app-wide recipientAddressBaseCheckEnabled flag, so offering it here let a setting that
// does nothing for BEAM silently turn screening on or off for every other chain.
@Composable
internal fun BeamSendScreen(
    title: String,
    viewModel: BeamSendViewModel,
    navController: NavController,
    inputType: AmountInputType,
    onToggleInputType: () -> Unit,
    onNext: (ProceedActionData) -> Unit,
) {
    val localNav = rememberNavController()
    val view = LocalView.current
    val context = LocalContext.current
    val currentOnNext by rememberUpdatedState(onNext)
    // Shared by the shared route's onLeave and the system BackHandler below: a bare pop would skip
    // the abort + network release leaveOfflineSign() does.
    val onLeaveSign = { if (viewModel.leaveOfflineSign()) localNav.popBackStackSafely() }
    val formActions = remember(viewModel) { viewModel.formActions() }

    LaunchedEffect(Unit) {
        // Read inside the lambda rather than passed as the collector: collect(currentOnNext) would
        // capture the first composition's lambda, which is what rememberUpdatedState prevents.
        viewModel.proceedRequests.collect { currentOnNext(it) }
    }
    LaunchedEffect(Unit) {
        viewModel.offlineSignRequests.collect { localNav.navigate(BeamOfflineSignPage) }
    }
    LaunchedEffect(Unit) {
        viewModel.errorEvents.collect { HudHelper.showErrorMessage(view, context.getString(it)) }
    }

    NavHost(localNav, startDestination = BeamSendFormPage) {
        composable(BeamSendFormPage) {
            val balanceHidden by viewModel.balanceHidden.collectAsStateWithLifecycle()
            BeamSendForm(
                title = title,
                state = viewModel.formState(balanceHidden),
                actions = formActions,
                navController = navController,
                inputType = inputType,
                onToggleInputType = onToggleInputType,
            )
        }
        offlineSignRoute(
            route = BeamOfflineSignPage,
            navController = localNav,
            stateProvider = {
                viewModel.reviewedQuote?.let {
                    OfflineSignRouteState(
                        viewModel.confirmationData(), viewModel.wallet.token.blockchain.name,
                        BeamAmount.DECIMALS, BeamAmount.DECIMALS, viewModel.coinRate, viewModel.signing.signState,
                    )
                }
            },
            onLeave = onLeaveSign,
            onSignClick = viewModel::onClickSignOffline,
            onSignStateConsumed = viewModel.signing::resetSignState,
            onSigned = { localNav.navigate(offlineTransactionTransferRoute(BeamOfflineTransferPage, it)) },
        )
        offlineTransactionTransferRoute(
            route = BeamOfflineTransferPage,
            formatArgument = "format",
            navController = localNav,
            transactionProvider = { viewModel.signing.signedTransaction },
            onDoneClick = {
                viewModel.signing.closeTransfer()
                if (!navController.popBackStack(R.id.sendXFragment, true)) navController.navigateUp()
            },
        )
    }

    val entry by localNav.currentBackStackEntryAsState()
    BackHandler(enabled = entry?.destination?.route == BeamOfflineSignPage, onBack = onLeaveSign)
}

// What the form reads from BeamSendViewModel, captured once per composition so the form and its
// sections take plain values instead of the view model.
private data class BeamSendFormState(
    val token: Token,
    val tokenType: TokenType,
    val recipient: String,
    val hideAddress: Boolean,
    val editable: Boolean,
    val amount: String,
    val availableToSend: BigDecimal,
    val amountUnique: AmountUnique?,
    val coinRate: CurrencyValue?,
    val balance: BigDecimal?,
    val balanceHidden: Boolean,
    val feeAmount: BigDecimal?,
    val feeLoading: Boolean,
    val canProceed: Boolean,
    val busy: Boolean,
    val offlineSignSupported: Boolean,
    val canSignOffline: Boolean,
)

private class BeamSendFormActions(
    val onRecipientChange: (String) -> Unit,
    val onEnterAmount: (BigDecimal?) -> Unit,
    val proceed: () -> Unit,
    val signOffline: () -> Unit,
    val toggleHideBalance: () -> Unit,
)

private fun BeamSendViewModel.formActions() = BeamSendFormActions(
    onRecipientChange = ::onRecipientChanged,
    onEnterAmount = ::onEnterAmount,
    proceed = ::proceed,
    signOffline = ::signOffline,
    toggleHideBalance = ::toggleHideBalance,
)

private fun BeamSendViewModel.formState(balanceHidden: Boolean) = BeamSendFormState(
    token = wallet.token,
    tokenType = wallet.token.type,
    recipient = recipient,
    hideAddress = hideAddress,
    editable = editable,
    amount = amount,
    availableToSend = availableToSend,
    amountUnique = amountUnique,
    coinRate = coinRate,
    balance = balance,
    balanceHidden = balanceHidden,
    feeAmount = feeAmount,
    feeLoading = feeLoading,
    canProceed = canProceed,
    busy = busy,
    offlineSignSupported = offlineSignSupported,
    canSignOffline = canSignOffline,
)

@Composable
private fun BeamSendForm(
    title: String,
    state: BeamSendFormState,
    actions: BeamSendFormActions,
    navController: NavController,
    inputType: AmountInputType,
    onToggleInputType: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    var percentageAmountUnique by remember { mutableStateOf<AmountUnique?>(null) }

    // SendSuggestionsBar only renders while the keyboard is up, so the percentage affordance that
    // replaced the bespoke Max button would stay hidden without this. Same as every other send screen.
    // Guarded on `editable`: the amount field is not composed once a send was attempted, and an
    // unattached FocusRequester logs "FocusRequester is not initialized" instead of doing anything.
    LaunchedEffect(state.editable) {
        if (state.editable) focusRequester.requestFocus()
    }

    // The shared SendScreen does not inset itself; every other send screen is hosted by a fragment
    // that does. BEAM's form is composed directly, so the inset belongs here.
    Box(Modifier.fillMaxSize().systemBarsPadding()) {
        SendScreen(
            title = title,
            proceedEnabled = state.canProceed,
            onCloseClick = navController::navigateUpSafely,
            onSendClick = actions.proceed,
            proceedTitle = TranslatableString.ResString(R.string.Button_Next),
            bottomOverlay = {
                SendSuggestionsBar(
                    availableBalance = state.availableToSend,
                    coinDecimal = BeamAmount.DECIMALS,
                    coinAmount = BeamAmount.parse(state.amount)
                        ?.let { BigDecimal.valueOf(it, BeamAmount.DECIMALS) },
                    onAmountChange = actions.onEnterAmount,
                    onPercentageAmountUnique = { percentageAmountUnique = it },
                )
            },
        ) {
            if (state.editable) {
                if (!state.hideAddress) BeamAddressSection(state, actions.onRecipientChange, navController)
                BeamAmountSection(state, actions, inputType, onToggleInputType, focusRequester, percentageAmountUnique)
            }
            VSpacer(12.dp)
            BeamFeeSection(state, actions.toggleHideBalance)
            OfflineSignActionCell(
                supported = state.offlineSignSupported,
                enabled = state.canSignOffline,
                onClick = actions.signOffline,
            )
            ButtonPrimaryYellow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
                title = stringResource(R.string.Button_Next),
                enabled = state.canProceed,
                loadingIndicator = state.busy,
                onClick = actions.proceed,
            )
        }
    }
}

@Composable
private fun BeamAmountSection(
    state: BeamSendFormState,
    actions: BeamSendFormActions,
    inputType: AmountInputType,
    onToggleInputType: () -> Unit,
    focusRequester: FocusRequester,
    percentageAmountUnique: AmountUnique?,
) {
    // availableToSend is fee-aware (the SDK's Max, or the balance less the fixed fee), so the shared
    // input's own MAX/percentage affordances hand back an amount BEAM can actually send.
    HSAmountInput(
        modifier = Modifier.padding(horizontal = 16.dp),
        focusRequester = focusRequester,
        availableBalance = state.availableToSend,
        coinCode = state.token.coin.code,
        coinDecimal = BeamAmount.DECIMALS,
        fiatDecimal = 2,
        onClickHint = onToggleInputType,
        onValueChange = actions.onEnterAmount,
        inputType = inputType,
        rate = state.coinRate,
        amountUnique = state.amountUnique,
        percentageAmountUnique = percentageAmountUnique,
    )
}

@Composable
private fun BeamFeeSection(state: BeamSendFormState, onBalanceClick: () -> Unit) {
    // BEAM pays the fee in the coin it sends, so feeCoinBalance stays null: that is the
    // isNativeCoinSwap path, which also drops the separate "balance for fees" row.
    FeeInfoSection(
        tokenIn = state.token,
        displayBalance = state.balance,
        balanceHidden = state.balanceHidden,
        feeToken = state.token,
        feeCoinBalance = null,
        feePrimary = feePrimaryText(state.token, state.feeAmount),
        feeSecondary = feeSecondaryText(state.feeAmount, state.coinRate),
        feeLoading = state.feeLoading,
        insufficientFeeBalance = false,
        onBalanceClicked = onBalanceClick,
    )
}

@Composable
private fun BeamAddressSection(
    state: BeamSendFormState,
    onRecipientChange: (String) -> Unit,
    navController: NavController,
) {
    val addressViewModel: AddressViewModel = viewModel {
        AddressViewModel(
            BlockchainType.Beam, getKoinInstance(), AddressUriParser(BlockchainType.Beam, state.tokenType),
            BeamRecipient.parserChain(), state.recipient.takeIf { it.isNotEmpty() }?.let(::Address),
        )
    }
    val currentOnRecipientChanged by rememberUpdatedState(onRecipientChange)
    val inputState by addressViewModel.inputState.collectAsStateWithLifecycle()
    val address by addressViewModel.address.collectAsStateWithLifecycle()
    val value by addressViewModel.value.collectAsStateWithLifecycle()
    // Observe raw edits too: generic parsing can retain the last valid address during validation.
    LaunchedEffect(value) { currentOnRecipientChanged(value.trim()) }
    HSAddressInput(
        modifier = Modifier.padding(horizontal = 16.dp),
        viewModel = addressViewModel, inputState = inputState, address = address, value = value,
        navController = navController,
    )
    VSpacer(12.dp)
}

@Composable
internal fun BeamSendConfirmationScreen(
    navController: NavController,
    viewModel: BeamSendViewModel,
    sendEntryPointDestId: Int,
) {
    if (viewModel.reviewedQuote == null) {
        // Not reachable after Phase 1 (Next always snapshots a quote first); kept as the same
        // recovery SendConfirmationFragment uses for a missing VM.
        LaunchedEffect(Unit) {
            if (!navController.popBackStack(R.id.sendXFragment, false)) navController.navigateUp()
        }
        return
    }

    val data = viewModel.confirmationData()
    SendConfirmationScreen(
        navController = navController,
        coinMaxAllowedDecimals = BeamAmount.DECIMALS,
        feeCoinMaxAllowedDecimals = BeamAmount.DECIMALS,
        rate = viewModel.coinRate,
        feeCoinRate = viewModel.coinRate,
        sendResult = viewModel.sendResult,
        blockchainType = BlockchainType.Beam,
        coin = data.coin,
        feeCoin = data.feeCoin,
        amount = data.amount,
        address = data.address,
        contact = data.contact,
        fee = data.fee,
        // BEAM fee is paid in the same coin as the send, so this is the one balance figure
        // the shared screen needs; feeCoinBalance stays unset (isNativeCoinSwap path), which
        // also skips the separate "balance for fees" row FeeInfoSection only shows when the
        // fee coin differs from the sent coin.
        displayBalance = viewModel.confirmationBalance,
        lockTimeInterval = null,
        memo = null,
        rbfEnabled = null,
        onClickSend = { beamDispatchConfirmation(navController, viewModel, sendEntryPointDestId) },
        sendEntryPointDestId = sendEntryPointDestId,
        isSynced = viewModel.ready,
        hasAdapterError = false,
        onRetrySync = {},
        sendEnabled = !viewModel.busy && (viewModel.ready || viewModel.recorded),
        onSignOfflineOnFailure = null,
        sendToken = viewModel.wallet.token,
    )
}

private fun beamDispatchConfirmation(navController: NavController, viewModel: BeamSendViewModel, entryPoint: Int) {
    when (viewModel.confirmationCommand()) {
        BeamConfirmationCommand.Finish -> {
            val destination = entryPoint.takeUnless { it == 0 } ?: R.id.sendXFragment
            if (!navController.popBackStack(destination, true)) navController.navigateUp()
        }
        BeamConfirmationCommand.Retry -> viewModel.retry()
        BeamConfirmationCommand.Confirm -> viewModel.confirm()
    }
}

internal enum class BeamConfirmationCommand { Finish, Retry, Confirm }

internal fun BeamSendViewModel.confirmationCommand(): BeamConfirmationCommand = when {
    recorded -> BeamConfirmationCommand.Finish
    attempted -> BeamConfirmationCommand.Retry
    else -> BeamConfirmationCommand.Confirm
}
