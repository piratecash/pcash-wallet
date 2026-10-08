package cash.p.terminal.modules.send.memo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import cash.p.terminal.modules.send.SendConfirmationData
import cash.p.terminal.modules.send.SendConfirmationScreen
import cash.p.terminal.modules.send.offline.OfflineSignableConfirmationHost
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import java.math.BigDecimal
import kotlin.reflect.KClass

@Composable
fun SendMemoConfirmationScreen(
    navigation: HSNavigation,
    sendViewModel: SendMemoViewModel,
    sendEntryPoint: KClass<out HSPage>?
) {
    OfflineSignableConfirmationHost(
        navigation = navigation,
        sendViewModel = sendViewModel,
        sourceChangeable = false,
        onChangeSourceClick = {},
    ) { onRequestOfflineSign ->
        MemoOnlineConfirmation(
            navigation = navigation,
            sendViewModel = sendViewModel,
            sendEntryPoint = sendEntryPoint,
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
    navigation: HSNavigation,
    sendViewModel: SendMemoViewModel,
    sendEntryPoint: KClass<out HSPage>?,
    onRequestOfflineSign: (() -> Unit)?,
) {
    val confirmationData = rememberConfirmationData(sendViewModel.uiState.fee, sendViewModel::getConfirmationData)

    SendConfirmationScreen(
        navigation = navigation,
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
        sendEntryPoint = sendEntryPoint,
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
