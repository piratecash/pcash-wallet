package cash.p.terminal.modules.send.memo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavController
import cash.p.terminal.modules.send.SendConfirmationData
import cash.p.terminal.modules.send.SendConfirmationScreen
import cash.p.terminal.modules.send.offline.OfflineSignFlowRoutes
import cash.p.terminal.modules.send.offline.OfflineSignableConfirmationHost
import java.math.BigDecimal

private const val MemoConfirmationPage = "stellar_confirmation"
private const val OfflineMemoSignPage = "offline_stellar_confirmation_sign"
private const val OfflineMemoTransactionTransferPage = "offline_stellar_confirmation_transfer"

@Composable
fun SendMemoConfirmationScreen(
    navController: NavController,
    sendViewModel: SendMemoViewModel,
    sendEntryPointDestId: Int
) {
    OfflineSignableConfirmationHost(
        fragmentNavController = navController,
        sendViewModel = sendViewModel,
        confirmationRoute = MemoConfirmationPage,
        signFlowRoutes = OfflineSignFlowRoutes(
            signRoute = OfflineMemoSignPage,
            transferRoute = OfflineMemoTransactionTransferPage,
        ),
        sourceChangeable = false,
        onChangeSourceClick = {},
    ) { onRequestOfflineSign ->
        MemoOnlineConfirmation(
            navController = navController,
            sendViewModel = sendViewModel,
            sendEntryPointDestId = sendEntryPointDestId,
            onRequestOfflineSign = onRequestOfflineSign,
        )
    }
}

// Reloaded when the live fee changes and when the screen resumes after a pause.
@Composable
internal fun rememberConfirmationData(
    fee: BigDecimal?,
    load: () -> SendConfirmationData,
): SendConfirmationData {
    var reloads by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        if (paused) reloads++
        onPauseOrDispose { paused = true }
    }
    return remember(fee, reloads) { load() }
}

@Composable
private fun MemoOnlineConfirmation(
    navController: NavController,
    sendViewModel: SendMemoViewModel,
    sendEntryPointDestId: Int,
    onRequestOfflineSign: (() -> Unit)?,
) {
    val confirmationData = rememberConfirmationData(sendViewModel.uiState.fee, sendViewModel::getConfirmationData)

    SendConfirmationScreen(
        navController = navController,
        coinMaxAllowedDecimals = sendViewModel.coinMaxAllowedDecimals,
        feeCoinMaxAllowedDecimals = sendViewModel.feeTokenMaxAllowedDecimals,
        rate = sendViewModel.coinRate,
        feeCoinRate = sendViewModel.feeCoinRate,
        sendResult = sendViewModel.sendResult,
        blockchainType = sendViewModel.blockchainType,
        coin = confirmationData.coin,
        feeCoin = confirmationData.feeCoin,
        amount = confirmationData.amount,
        address = confirmationData.address,
        contact = confirmationData.contact,
        fee = confirmationData.fee,
        lockTimeInterval = confirmationData.lockTimeInterval,
        memo = confirmationData.memo,
        rbfEnabled = confirmationData.rbfEnabled,
        onClickSend = sendViewModel::onClickSend,
        sendEntryPointDestId = sendEntryPointDestId,
        isSynced = sendViewModel.isSynced,
        hasAdapterError = sendViewModel.hasAdapterError,
        onRetrySync = sendViewModel::retryAdapterSync,
        sendEnabled = sendViewModel.isEffectivelySynced,
        onSignOfflineOnFailure = onRequestOfflineSign,
        sendToken = sendViewModel.wallet.token,
        feeToken = sendViewModel.feeToken,
        feeCoinBalance = sendViewModel.feeCoinBalance,
        displayBalance = sendViewModel.displayBalance,
        insufficientFeeBalance = sendViewModel.isInsufficientFeeBalance(confirmationData.fee),
        balanceHidden = sendViewModel.balanceHidden,
        onBalanceClicked = sendViewModel::toggleHideBalance,
    )
}
