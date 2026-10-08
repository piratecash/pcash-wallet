package cash.p.terminal.modules.transactionInfo.options

import android.os.Parcelable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.viewmodel.compose.viewModel
import cash.p.terminal.R
import cash.p.terminal.modules.confirm.ConfirmTransactionScreen
import cash.p.terminal.modules.offline.rememberOfflineGatedAction
import cash.p.terminal.modules.sendevmtransaction.SendEvmTransactionView
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.HudHelper
import cash.p.terminal.ui_compose.components.SnackbarDuration
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.logger.AppLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

class TransactionSpeedUpCancelPage(val input: Input) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        TransactionSpeedUpCancelScreen(navigation, this, input)
    }

    @Parcelize
    data class Input(
        val blockchainType: BlockchainType,
        val optionType: SpeedUpCancelType,
        val transactionHash: String
    ) : Parcelable

    @Parcelize
    data class Result(val success: Boolean) : Parcelable
}

@Composable
private fun TransactionSpeedUpCancelScreen(
    navigation: HSNavigation,
    page: TransactionSpeedUpCancelPage,
    input: TransactionSpeedUpCancelPage.Input
) {
    val logger = remember { AppLogger("tx-speedUp-cancel") }
    val view = LocalView.current

    val viewModel = viewModel<TransactionSpeedUpCancelViewModel>(
        factory = TransactionSpeedUpCancelViewModel.Factory(
            input.blockchainType,
            input.transactionHash,
            input.optionType,
        )
    )

    val uiState = viewModel.uiState
    val offlineGatedAction = rememberOfflineGatedAction(viewModel.wallet)

    LaunchedEffect(uiState.error) {
        if (uiState.error is TransactionAlreadyInBlock) {
            HudHelper.showErrorMessage(
                view,
                R.string.TransactionInfoOptions_Warning_TransactionInBlock
            )
            navigation.navigateUp()
        }
    }

    val sendTransactionState = uiState.sendTransactionState

    ConfirmTransactionScreen(
        title = viewModel.title,
        onClickBack = navigation::navigateUpSafely,
        onClickSettings = {
            navigation.slideFromBottom(TransactionSpeedUpCancelTransactionSettingsPage())
        },
        onClickClose = null,
        buttonsSlot = {
            val buttonTitle = viewModel.buttonTitle
            val coroutineScope = rememberCoroutineScope()
            var buttonEnabled by remember { mutableStateOf(true) }

            ButtonPrimaryYellow(
                modifier = Modifier.fillMaxWidth(),
                title = buttonTitle,
                onClick = {
                    offlineGatedAction.onClick(uiState.sendAvailability) {
                        logger.info("click $buttonTitle button")

                        coroutineScope.launch {
                            buttonEnabled = false
                            HudHelper.showInProcessMessage(
                                view,
                                R.string.Send_Sending,
                                SnackbarDuration.INDEFINITE
                            )

                            val result = try {
                                logger.info("sending tx")
                                viewModel.send()
                                logger.info("success")

                                HudHelper.showSuccessMessage(view, R.string.Hud_Text_Done)
                                delay(1200)
                                TransactionSpeedUpCancelPage.Result(true)
                            } catch (t: Throwable) {
                                logger.warning("failed", t)
                                HudHelper.showErrorMessage(view, t.javaClass.simpleName)
                                TransactionSpeedUpCancelPage.Result(false)
                            }

                            buttonEnabled = true
                            navigation.setResult(page, result)
                            navigation.navigateUp()
                        }
                    }
                },
                enabled = uiState.sendAvailability.clickable && buttonEnabled
            )
        }
    ) {
        SendEvmTransactionView(
            navigation,
            uiState.sectionViewItems,
            sendTransactionState.cautions,
            sendTransactionState.fields,
            sendTransactionState.networkFee,
        )
    }

    offlineGatedAction.Sheet()
}
