package cash.p.terminal.modules.send.tron

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cash.p.terminal.R
import cash.p.terminal.entities.Address
import cash.p.terminal.modules.address.AddressParserModule
import cash.p.terminal.modules.address.AddressParserViewModel
import cash.p.terminal.modules.address.AmountUnique
import cash.p.terminal.modules.address.HSAddressInput
import cash.p.terminal.modules.amount.AmountInputModeViewModel
import cash.p.terminal.modules.amount.HSAmountInput
import cash.p.terminal.modules.fee.FeeInfoSection
import cash.p.terminal.modules.send.SendConfirmationPage
import cash.p.terminal.modules.send.SendPage.ProceedActionData
import cash.p.terminal.modules.send.SendScreen
import cash.p.terminal.modules.send.SendSuggestionsBar
import cash.p.terminal.modules.send.address.AddressCheckerControl
import cash.p.terminal.modules.send.address.SmartContractCheckSection
import cash.p.terminal.modules.send.offline.OfflineSignActionCell
import cash.p.terminal.modules.send.offline.OfflineSignPage
import cash.p.terminal.modules.sendtokenselect.PrefilledData
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.ui.compose.components.PoisonAddressRiskSection
import cash.p.terminal.ui.compose.components.PoisonWarningCell
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.SectionUniversalLawrence
import cash.p.terminal.ui_compose.components.SwitchWithText
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import java.math.BigDecimal

@Composable
@Suppress("ViewModelForwarding")
fun SendTronPageContent(
    title: String,
    navigation: HSNavigation,
    viewModel: SendTronViewModel,
    amountInputModeViewModel: AmountInputModeViewModel,
    prefilledData: PrefilledData?,
    addressCheckerControl: AddressCheckerControl,
    onNextClick: (ProceedActionData) -> Unit,
) {
    SendTronScreen(
        title = title,
        navigation = navigation,
        viewModel = viewModel,
        amountInputModeViewModel = amountInputModeViewModel,
        prefilledData = prefilledData,
        addressCheckerControl = addressCheckerControl,
        onOfflineSignClick = { navigation.slideFromRight(OfflineSignPage(SendTronViewModel::class)) },
        onNextClick = onNextClick,
    )
}

@Composable
@Suppress("LongMethod", "LongParameterList")
fun SendTronScreen(
    title: String,
    navigation: HSNavigation,
    viewModel: SendTronViewModel,
    amountInputModeViewModel: AmountInputModeViewModel,
    prefilledData: PrefilledData?,
    addressCheckerControl: AddressCheckerControl,
    onOfflineSignClick: () -> Unit,
    onNextClick: (ProceedActionData) -> Unit,
) {
    val wallet = viewModel.wallet
    val uiState = viewModel.uiState

    val availableBalance = uiState.availableBalance
    val amountCaution = uiState.amountCaution
    val proceedEnabled = uiState.proceedEnabled
    val amountInputType = amountInputModeViewModel.inputType

    val paymentAddressViewModel = viewModel<AddressParserViewModel>(
        factory = AddressParserModule.Factory(wallet.token, prefilledData)
    )
    val amountUnique = paymentAddressViewModel.amountUnique


    ComposeAppTheme {
        val focusRequester = remember { FocusRequester() }
        var percentageAmountUnique by remember { mutableStateOf<AmountUnique?>(null) }
        var coinAmount by remember { mutableStateOf<BigDecimal?>(null) }
        val onProceed = {
            viewModel.onNavigateToConfirmation()
            onNextClick(
                ProceedActionData(
                    address = uiState.address?.hex,
                    wallet = wallet,
                    type = SendConfirmationPage.Type.Tron,
                )
            )
        }

        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }

        SendScreen(
            title = title,
            onCloseClick = { navigation.navigateUpSafely() },
            proceedEnabled = proceedEnabled,
            onSendClick = onProceed,
            bottomOverlay = {
                SendSuggestionsBar(
                    availableBalance = availableBalance,
                    coinDecimal = viewModel.coinMaxAllowedDecimals,
                    coinAmount = coinAmount,
                    onAmountChange = { amount ->
                        coinAmount = amount
                        viewModel.onEnterAmount(amount)
                    },
                    onPercentageAmountUnique = { percentageAmountUnique = it },
                )
            }
        ) {
            if (uiState.isPoisonAddress) {
                PoisonWarningCell()
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (uiState.showAddressInput) {
                HSAddressInput(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    initial = prefilledData?.address?.let { Address(it) },
                    tokenQuery = wallet.token.tokenQuery,
                    coinCode = wallet.coin.code,
                    error = uiState.addressError,
                    textPreprocessor = paymentAddressViewModel,
                    navigation = navigation,
                    isPoisonAddress = uiState.isPoisonAddress,
                    onValueChange = { viewModel.onEnterAddress(it) },
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            HSAmountInput(
                modifier = Modifier.padding(horizontal = 16.dp),
                focusRequester = focusRequester,
                availableBalance = availableBalance,
                caution = amountCaution,
                coinCode = wallet.coin.code,
                coinDecimal = viewModel.coinMaxAllowedDecimals,
                fiatDecimal = viewModel.fiatMaxAllowedDecimals,
                onClickHint = {
                    amountInputModeViewModel.onToggleInputType()
                },
                onValueChange = {
                    coinAmount = it
                    viewModel.onEnterAmount(it)
                },
                inputType = amountInputType,
                rate = viewModel.coinRate,
                amountUnique = amountUnique,
                percentageAmountUnique = percentageAmountUnique,
            )

            Spacer(modifier = Modifier.height(12.dp))
            FeeInfoSection(
                tokenIn = wallet.token,
                displayBalance = viewModel.displayBalance,
                balanceHidden = viewModel.balanceHidden,
                feeToken = viewModel.feeToken,
                feeCoinBalance = viewModel.feeCoinBalance,
                feePrimary = viewModel.formatFeePrimary(uiState.fee),
                feeSecondary = viewModel.formatFeeSecondary(uiState.fee, viewModel.feeCoinRate),
                insufficientFeeBalance = viewModel.isInsufficientFeeBalance(uiState.fee),
                onBalanceClicked = viewModel::toggleHideBalance,
                feeLoading = uiState.feeLoading,
            )

            Spacer(modifier = Modifier.height(12.dp))
            SectionUniversalLawrence {
                SwitchWithText(
                    text = stringResource(R.string.SettingsAddressChecker_RecipientCheck),
                    checked = addressCheckerControl.uiState.addressCheckByBaseEnabled,
                    onCheckedChange = addressCheckerControl::onCheckBaseAddressClick
                )
            }
            SmartContractCheckSection(
                token = wallet.token,
                navigation = navigation,
                addressCheckerControl = addressCheckerControl,
                modifier = Modifier.padding(top = 8.dp)
            )

            PoisonAddressRiskSection(
                isPoisonAddress = uiState.isPoisonAddress,
                riskAccepted = uiState.riskAccepted,
                onRiskAcceptedChange = { viewModel.onRiskAcceptedChange(it) },
            )

            OfflineSignActionCell(
                supported = viewModel.offlineSignSupported,
                enabled = proceedEnabled && uiState.sendEnabled,
                onClick = onOfflineSignClick,
            )

            ButtonPrimaryYellow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                title = stringResource(R.string.Send_DialogProceed),
                onClick = onProceed,
                enabled = proceedEnabled
            )
        }
    }

}
