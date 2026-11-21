package cash.p.terminal.modules.tonconnect

import androidx.compose.animation.Crossfade
import cash.p.terminal.resources.unknown_error
import cash.p.terminal.resources.Send_Sending
import cash.p.terminal.resources.Send_Confirmation_Action
import cash.p.terminal.resources.Hud_Text_Done
import cash.p.terminal.resources.Button_Reject
import cash.p.terminal.resources.Button_Confirm
import cash.p.terminal.resources.Button_Close
import cash.p.terminal.resources.Res
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import androidx.compose.ui.unit.dp
import cash.p.terminal.feature.wallet.rememberSecretAction
import cash.p.terminal.entities.transactionrecords.ton.TonTransactionRecord
import cash.p.terminal.modules.confirm.ConfirmTransactionScreen
import cash.p.terminal.modules.xtransaction.cells.HeaderCell
import cash.p.terminal.modules.xtransaction.helpers.TransactionInfoHelper
import cash.p.terminal.modules.xtransaction.sections.BurnSection
import cash.p.terminal.modules.xtransaction.sections.FeeSection
import cash.p.terminal.modules.xtransaction.sections.MintSection
import cash.p.terminal.modules.xtransaction.sections.ReceiveCoinSection
import cash.p.terminal.modules.xtransaction.sections.SendCoinSection
import cash.p.terminal.modules.xtransaction.sections.SwapSection
import cash.p.terminal.modules.xtransaction.sections.ton.ContractCallSection
import cash.p.terminal.modules.xtransaction.sections.ton.ContractDeploySection
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.ui_compose.components.ButtonPrimaryDefault
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.LocalHudMessenger
import cash.p.terminal.ui_compose.components.SectionUniversalLawrence
import cash.p.terminal.ui_compose.components.SnackbarDuration
import cash.p.terminal.ui_compose.components.TextImportantError
import cash.p.terminal.ui_compose.components.VSpacer
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.logger.AppLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TonConnectSendRequestScreen(
    navigation: HSNavigation,
    viewModel: TonConnectSendRequestViewModel,
) {
    val logger = remember { AppLogger("ton-connect request") }
    val uiState = viewModel.uiState
    val hud = LocalHudMessenger.current
    val sendingText = stringResource(Res.string.Send_Sending)
    val doneText = stringResource(Res.string.Hud_Text_Done)
    val coroutineScope = rememberCoroutineScope()
    var buttonEnabled by remember { mutableStateOf(true) }
    val confirm = navigation.rememberSecretAction {
        coroutineScope.launch {
            buttonEnabled = false
            hud.showInProgress(sendingText, SnackbarDuration.INDEFINITE)

            try {
                logger.info("click confirm button")
                viewModel.confirm()
            } catch (t: Throwable) {
                logger.warning("failed", t)
                hud.showError(t.message ?: t.javaClass.simpleName)
            }

            buttonEnabled = true
        }
    }

    // The indefinite Sending HUD would cover the Close button.
    LaunchedEffect(uiState.error) {
        if (uiState.error != null) hud.dismiss()
    }

    LaunchedEffect(uiState.success) {
        if (uiState.success) {
            logger.info("success")
            hud.showSuccess(doneText)
            delay(1200)
            navigation.navigateUp()
        }
    }

    ConfirmTransactionScreen(
        onClickBack = navigation::navigateUpSafely,
        onClickSettings = null,
        onClickClose = null,
        buttonsSlot = {
            if (uiState.error != null) {
                ButtonPrimaryDefault(
                    modifier = Modifier.fillMaxWidth(),
                    title = stringResource(Res.string.Button_Close),
                    enabled = true,
                    onClick = {
                        navigation.navigateUpSafely()
                    }
                )
            } else {
                ButtonPrimaryYellow(
                    modifier = Modifier.fillMaxWidth(),
                    title = stringResource(Res.string.Button_Confirm),
                    enabled = uiState.confirmEnabled && buttonEnabled,
                    onClick = confirm
                )
                VSpacer(16.dp)
                ButtonPrimaryDefault(
                    modifier = Modifier.fillMaxWidth(),
                    title = stringResource(Res.string.Button_Reject),
                    enabled = uiState.rejectEnabled,
                    onClick = {
                        viewModel.reject()
                        navigation.navigateUpSafely()
                    }
                )
            }
        }
    ) {
        uiState.error?.let { error ->
            TextImportantError(
                modifier = Modifier.padding(horizontal = 16.dp),
                text = if (error is TonConnectSendRequestError.KitUnavailable) {
                    stringResource(Res.string.unknown_error)
                } else {
                    error.message ?: error.javaClass.simpleName
                }
            )
        }

        Crossfade(uiState.tonTransactionRecord) { record ->
            if (record != null) {
                val transactionInfoHelper = koinInject<TransactionInfoHelper>()

                Column {
                    record.actions.forEachIndexed { index, action ->
                        if (index != 0) {
                            VSpacer(12.dp)
                        }
                        TonConnectRequestActionSection(
                            action = action,
                            transactionInfoHelper = transactionInfoHelper,
                            navigation = navigation
                        )
                    }
                    VSpacer(12.dp)

                    FeeSection(
                        transactionInfoHelper = transactionInfoHelper,
                        fee = record.fee,
                        navigation = navigation
                    )
                }
            }
        }
    }
}

@Composable
fun TonConnectRequestActionSection(
    action: TonTransactionRecord.Action,
    transactionInfoHelper: TransactionInfoHelper,
    navigation: HSNavigation,
) {
    when (val actionType = action.type) {
        is TonTransactionRecord.Action.Type.Burn -> {
            BurnSection(
                transactionValue = actionType.value,
                transactionInfoHelper = transactionInfoHelper,
                navigation = navigation
            )
        }

        is TonTransactionRecord.Action.Type.ContractCall -> {
            ContractCallSection(
                navigation = navigation,
                operation = actionType.operation,
                address = actionType.address,
                transactionValue = actionType.value,
                transactionInfoHelper = transactionInfoHelper,
                blockchainType = BlockchainType.Ton
            )
        }

        is TonTransactionRecord.Action.Type.ContractDeploy -> {
            ContractDeploySection(
                interfaces = actionType.interfaces
            )
        }

        is TonTransactionRecord.Action.Type.Mint -> {
            MintSection(
                transactionValue = actionType.value,
                transactionInfoHelper = transactionInfoHelper,
                navigation = navigation
            )
        }

        is TonTransactionRecord.Action.Type.Receive -> {
            ReceiveCoinSection(
                transactionValue = actionType.value,
                address = actionType.from,
                comment = actionType.comment,
                navigation = navigation,
                transactionInfoHelper = transactionInfoHelper,
                blockchainType = BlockchainType.Ton
            )
        }

        is TonTransactionRecord.Action.Type.Send -> {
            SendCoinSection(
                transactionValue = actionType.value,
                address = actionType.to,
                comment = actionType.comment,
                sentToSelf = actionType.sentToSelf,
                navigation = navigation,
                transactionInfoHelper = transactionInfoHelper,
                blockchainType = BlockchainType.Ton
            )
        }

        is TonTransactionRecord.Action.Type.LiquidityDeposit -> {
            actionType.deposits.forEach { deposit ->
                SendCoinSection(
                    transactionValue = deposit.value,
                    address = deposit.vault,
                    comment = null,
                    sentToSelf = false,
                    navigation = navigation,
                    transactionInfoHelper = transactionInfoHelper,
                    blockchainType = BlockchainType.Ton
                )
            }
        }

        is TonTransactionRecord.Action.Type.Swap -> {
            SwapSection(
                transactionInfoHelper = transactionInfoHelper,
                navigation = navigation,
                transactionValueIn = actionType.valueIn,
                transactionValueOut = actionType.valueOut
            )
        }

        is TonTransactionRecord.Action.Type.Unsupported -> {
            SectionUniversalLawrence {
                HeaderCell(
                    title = stringResource(Res.string.Send_Confirmation_Action),
                    value = actionType.type,
                    painter = null
                )
            }
        }
    }
}
