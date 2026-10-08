package cash.p.terminal.modules.send.monero

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import cash.p.terminal.modules.send.SendConfirmationScreen
import cash.p.terminal.modules.send.offline.OfflineSignableConfirmationHost
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import kotlin.reflect.KClass

@Composable
fun SendMoneroConfirmationScreen(
    navigation: HSNavigation,
    sendViewModel: SendMoneroViewModel,
    sendEntryPoint: KClass<out HSPage>?
) {
    OfflineSignableConfirmationHost(
        navigation = navigation,
        sendViewModel = sendViewModel,
        sourceChangeable = false,
        onChangeSourceClick = {},
    ) { onRequestOfflineSign ->
        MoneroOnlineConfirmation(
            navigation = navigation,
            sendViewModel = sendViewModel,
            sendEntryPoint = sendEntryPoint,
            onRequestOfflineSign = onRequestOfflineSign,
        )
    }
}

@Composable
private fun MoneroOnlineConfirmation(
    navigation: HSNavigation,
    sendViewModel: SendMoneroViewModel,
    sendEntryPoint: KClass<out HSPage>?,
    onRequestOfflineSign: (() -> Unit)?,
) {
    var confirmationData by remember { mutableStateOf(sendViewModel.getConfirmationData()) }
    var refresh by remember { mutableStateOf(false) }

    LifecycleResumeEffect(sendViewModel) {
        if (refresh) {
            confirmationData = sendViewModel.getConfirmationData()
        }

        onPauseOrDispose {
            refresh = true
        }
    }

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
        // Online send re-estimates and builds a different transaction, so it must not show
        // the fee of the cached offline-signed one.
        fee = sendViewModel.fee,
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
        insufficientFeeBalance = sendViewModel.isInsufficientFeeBalance(sendViewModel.fee),
        balanceHidden = sendViewModel.balanceHidden,
        onBalanceClicked = sendViewModel::toggleHideBalance,
    )
}
