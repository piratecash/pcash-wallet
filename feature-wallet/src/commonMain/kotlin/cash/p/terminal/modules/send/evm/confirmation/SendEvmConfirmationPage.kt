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
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.send.evm.settings.SendEvmSettingsPage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.core.HardwareWalletSupport
import cash.p.terminal.modules.confirm.ConfirmTransactionScreen
import cash.p.terminal.modules.send.evm.SendEvmData
import cash.p.terminal.modules.send.evm.SendEvmModule
import cash.p.terminal.modules.sendevmtransaction.SendEvmTransactionView
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.SnackbarDuration
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import io.horizontalsystems.core.Translator
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.logger.AppLogger
import io.horizontalsystems.ethereumkit.api.jsonrpc.JsonRpc
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.TransactionData
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.reflect.KClass
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Hud_Text_Done
import cash.p.terminal.resources.Send_Confirmation_Send_Button
import cash.p.terminal.resources.Send_Sending
import cash.p.terminal.resources.unknown_send_error
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

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

    val viewModel = koinViewModel<SendEvmConfirmationViewModel> {
        parametersOf(input.transactionData, input.additionalInfo, input.blockchainType)
    }
    val uiState = viewModel.uiState

    ConfirmTransactionScreen(
        onClickBack = navigation::navigateUpSafely,
        onClickSettings = {
            navigation.slideFromBottom(SendEvmSettingsPage())
        },
        onClickClose = null,
        buttonsSlot = {
            val coroutineScope = rememberCoroutineScope()
            val hud = rememberHudMessenger()

            var buttonEnabled by remember { mutableStateOf(true) }

            ButtonPrimaryYellow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp),
                title = stringResource(Res.string.Send_Confirmation_Send_Button),
                onClick = {
                    logger.info("click send button")

                    coroutineScope.launch {
                        buttonEnabled = false
                        hud.showInProgress(Translator.getString(Res.string.Send_Sending), SnackbarDuration.INDEFINITE)

                        try {
                            logger.info("sending tx")
                            viewModel.send()
                            logger.info("success")

                            hud.showSuccess(Translator.getString(Res.string.Hud_Text_Done))
                            delay(1200)

                            navigation.removeLastUntil(input.sendEntryPoint, true)
                        } catch (t: Throwable) {
                            if (HardwareWalletSupport.isUserCancellation(t)) {
                                logger.info("user cancelled")
                                hud.dismiss()
                            } else {
                                logger.warning("failed", t)
                                val errorMsg = if (t is JsonRpc.ResponseError.RpcError) {
                                    t.error.message
                                } else {
                                    t.message ?: Translator.getString(Res.string.unknown_send_error)
                                }
                                hud.showError(errorMsg)
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
