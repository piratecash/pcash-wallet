package cash.p.terminal.modules.walletconnect.request.sendtransaction

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cash.p.terminal.R
import cash.p.terminal.core.ethereum.evmSendErrorMessage
import cash.p.terminal.modules.confirm.ConfirmTransactionScreen
import cash.p.terminal.modules.send.signingErrorText
import cash.p.terminal.modules.sendevmtransaction.SendEvmTransactionView
import cash.p.terminal.modules.walletconnect.request.WCEvmTransactionSettingsPage
import cash.p.terminal.modules.walletconnect.request.SessionRequestUI
import cash.p.terminal.modules.walletconnect.request.WCRequestPage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.navigation.viewModelStoreOwnerForPage
import cash.p.terminal.resources.Button_Confirm
import cash.p.terminal.resources.Button_Reject
import cash.p.terminal.resources.Button_Retry
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Send_Sending
import cash.p.terminal.ui_compose.components.ButtonPrimaryDefault
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.HudHelper
import cash.p.terminal.ui_compose.components.SnackbarDuration
import cash.p.terminal.ui_compose.components.VSpacer
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.logger.AppLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.padding
import cash.p.terminal.modules.walletconnect.WCVerifyAlertCard

@Composable
fun WCSendEthRequestScreen(
    navigation: HSNavigation,
    logger: AppLogger,
    blockchainType: BlockchainType,
    transaction: WalletConnectTransaction,
    request: SessionRequestUI.Content,
) {
    val viewModel = viewModel<WCSendEthereumTransactionRequestViewModel>(
        viewModelStoreOwner = navigation.viewModelStoreOwnerForPage(WCRequestPage::class),
        factory = WCSendEthereumTransactionRequestViewModel.Factory(
            blockchainType = blockchainType,
            transaction = transaction,
            request = request
        )
    )
    val uiState = viewModel.uiState

    ConfirmTransactionScreen(
        onClickBack = navigation::navigateUpSafely,
        onClickSettings = {
            navigation.slideFromBottom(WCEvmTransactionSettingsPage())
        },
        onClickClose = null,
        buttonsSlot = {
            WCSendEthButtons(
                retryAvailable = uiState.retryAvailable,
                sendEnabled = uiState.sendEnabled,
                logger = logger,
                navigation = navigation,
                onRetry = viewModel::retry,
                onConfirm = viewModel::confirm,
                onReject = viewModel::reject
            )
        }
    ) {
        WCVerifyAlertCard(request.verifyAlert, Modifier.padding(horizontal = 16.dp))
        SendEvmTransactionView(
            navigation,
            uiState.sectionViewItems,
            uiState.cautions,
            uiState.transactionFields,
            uiState.networkFee,
        )
    }
}

@Composable
private fun ColumnScope.WCSendEthButtons(
    retryAvailable: Boolean,
    sendEnabled: Boolean,
    logger: AppLogger,
    navigation: HSNavigation,
    onRetry: () -> Unit,
    onConfirm: suspend () -> Unit,
    onReject: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val view = LocalView.current

    var buttonEnabled by remember { mutableStateOf(true) }
    val sendingText = stringResource(Res.string.Send_Sending)

    if (retryAvailable) {
        ButtonPrimaryYellow(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(Res.string.Button_Retry),
            onClick = onRetry
        )
    } else {
        ButtonPrimaryYellow(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(Res.string.Button_Confirm),
            enabled = sendEnabled && buttonEnabled,
            onClick = {
                coroutineScope.launch {
                    buttonEnabled = false
                    val sendingHud =
                        HudHelper.showInProcessMessage(view, sendingText, SnackbarDuration.INDEFINITE)

                    try {
                        logger.info("click confirm button")
                        onConfirm()
                        logger.info("success")

                        HudHelper.showSuccessMessage(view, R.string.Hud_Text_Done)
                        delay(1200)
                        navigation.navigateUp()
                    } catch (t: Throwable) {
                        // The request stays pending: the user can confirm again or reject it.
                        logger.warning("failed", t)
                        val errorText = t.signingErrorText(t.evmSendErrorMessage())
                        if (errorText != null) {
                            HudHelper.showErrorMessage(view, errorText)
                        } else {
                            sendingHud?.dismiss()
                        }
                        buttonEnabled = true
                    }
                }
            }
        )
    }
    VSpacer(16.dp)
    ButtonPrimaryDefault(
        modifier = Modifier.fillMaxWidth(),
        title = stringResource(Res.string.Button_Reject),
        onClick = {
            onReject()
            navigation.navigateUpSafely()
        }
    )
}
