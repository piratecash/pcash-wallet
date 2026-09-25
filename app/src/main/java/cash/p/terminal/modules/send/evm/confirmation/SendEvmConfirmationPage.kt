package cash.p.terminal.modules.send.evm.confirmation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cash.p.terminal.R
import cash.p.terminal.modules.send.evm.settings.SendEvmSettingsPage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.trezor.domain.TrezorCancelledException
import cash.p.terminal.core.App
import cash.p.terminal.modules.confirm.ConfirmTransactionScreen
import cash.p.terminal.modules.send.evm.SendEvmData
import cash.p.terminal.modules.send.evm.SendEvmModule
import cash.p.terminal.modules.sendevmtransaction.SendEvmTransactionView
import cash.p.terminal.tangem.domain.isHardwareWalletUserCancelled
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.HudHelper
import cash.p.terminal.ui_compose.components.SnackbarDuration
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.logger.AppLogger
import io.horizontalsystems.ethereumkit.api.jsonrpc.JsonRpc
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.TransactionData
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

class SendEvmConfirmationPage(val input: Input) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        SendEvmConfirmationScreen(navigation, input)
    }

    data class Input(
        val transactionDataParcelable: SendEvmModule.TransactionDataParcelable,
        val additionalInfo: SendEvmData.AdditionalInfo?,
        val blockchainType: BlockchainType,
        val sendEntryPoint: KClass<out HSPage>
    ) {
        val transactionData: TransactionData
            get() = TransactionData(
                Address(transactionDataParcelable.toAddress),
                transactionDataParcelable.value,
                transactionDataParcelable.input
            )

        constructor(
            sendData: SendEvmData,
            blockchainType: BlockchainType,
            sendEntryPoint: KClass<out HSPage>
        ) : this(
            SendEvmModule.TransactionDataParcelable(sendData.transactionData),
            sendData.additionalInfo,
            blockchainType,
            sendEntryPoint
        )
    }
}

@Composable
private fun SendEvmConfirmationScreen(
    navigation: HSNavigation,
    input: SendEvmConfirmationPage.Input
) {
    val logger = remember { AppLogger("send-evm") }

    val viewModel = viewModel<SendEvmConfirmationViewModel>(
        factory = SendEvmConfirmationViewModel.Factory(
            transactionData = input.transactionData,
            additionalInfo = input.additionalInfo,
            blockchainType = input.blockchainType,
        )
    )
    val uiState = viewModel.uiState

    ConfirmTransactionScreen(
        onClickBack = navigation::navigateUpSafely,
        onClickSettings = {
            navigation.slideFromBottom(SendEvmSettingsPage())
        },
        onClickClose = null,
        buttonsSlot = {
            val coroutineScope = rememberCoroutineScope()
            val view = LocalView.current

            var buttonEnabled by remember { mutableStateOf(true) }

            ButtonPrimaryYellow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp),
                title = stringResource(R.string.Send_Confirmation_Send_Button),
                onClick = {
                    logger.info("click send button")

                    coroutineScope.launch {
                        buttonEnabled = false
                        val currentSnackbar = HudHelper.showInProcessMessage(
                            view,
                            R.string.Send_Sending,
                            SnackbarDuration.INDEFINITE
                        )

                        try {
                            logger.info("sending tx")
                            viewModel.send()
                            logger.info("success")

                            HudHelper.showSuccessMessage(view, R.string.Hud_Text_Done)
                            delay(1200)

                            navigation.removeLastUntil(input.sendEntryPoint, true)
                        } catch (e: TrezorCancelledException) {
                            logger.info("trezor user cancelled")
                            currentSnackbar?.dismiss()
                        } catch (t: Throwable) {
                            if (t.isHardwareWalletUserCancelled()) {
                                logger.info("user cancelled")
                                currentSnackbar?.dismiss()
                            } else {
                                logger.warning("failed", t)
                                val errorMsg = if (t is JsonRpc.ResponseError.RpcError) {
                                    t.error.message
                                } else {
                                    t.message ?: App.instance.getString(R.string.unknown_send_error)
                                }
                                HudHelper.showErrorMessage(view, errorMsg)
                            }
                        }

                        buttonEnabled = true
                    }
                },
                enabled = uiState.sendEnabled && buttonEnabled
            )
        }
    ) {
        SendEvmTransactionView(
            navigation,
            uiState.sectionViewItems,
            uiState.cautions,
            uiState.transactionFields,
            uiState.networkFee,
        )
    }
}
