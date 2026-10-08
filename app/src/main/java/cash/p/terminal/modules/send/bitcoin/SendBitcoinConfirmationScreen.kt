package cash.p.terminal.modules.send.bitcoin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import cash.p.terminal.modules.btcblockchainsettings.BtcBlockchainSettingsPage
import cash.p.terminal.modules.send.SendConfirmationScreen
import cash.p.terminal.modules.send.offline.OfflineSignableConfirmationHost
import cash.p.terminal.modules.syncerror.SyncErrorModule
import cash.p.terminal.modules.syncerror.SyncErrorViewModel
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import kotlin.reflect.KClass

@Composable
fun SendBitcoinConfirmationScreen(
    navigation: HSNavigation,
    sendViewModel: SendBitcoinViewModel,
    sendEntryPoint: KClass<out HSPage>?
) {
    val syncErrorViewModel = viewModel<SyncErrorViewModel>(
        factory = SyncErrorModule.Factory(sendViewModel.wallet)
    )
    OfflineSignableConfirmationHost(
        navigation = navigation,
        sendViewModel = sendViewModel,
        sourceChangeable = syncErrorViewModel.sourceChangeable,
        onChangeSourceClick = {
            navigation.openBitcoinSourceSettings(syncErrorViewModel.blockchainWrapper)
        },
    ) { onRequestOfflineSign ->
        BitcoinOnlineConfirmation(
            navigation = navigation,
            sendViewModel = sendViewModel,
            sendEntryPoint = sendEntryPoint,
            onRequestOfflineSign = onRequestOfflineSign,
        )
    }
}

@Composable
private fun BitcoinOnlineConfirmation(
    navigation: HSNavigation,
    sendViewModel: SendBitcoinViewModel,
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

    LaunchedEffect(sendViewModel.isSynced) {
        if (sendViewModel.isSynced) {
            confirmationData = sendViewModel.getConfirmationData()
        }
    }

    SendConfirmationScreen(
        navigation = navigation,
        coinMaxAllowedDecimals = sendViewModel.coinMaxAllowedDecimals,
        feeCoinMaxAllowedDecimals = sendViewModel.coinMaxAllowedDecimals,
        rate = sendViewModel.coinRate,
        feeCoinRate = sendViewModel.coinRate,
        sendResult = sendViewModel.sendResult,
        blockchainType = sendViewModel.blockchainType,
        coin = confirmationData.coin,
        feeCoin = confirmationData.coin,
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

private fun HSNavigation.openBitcoinSourceSettings(
    blockchainWrapper: SyncErrorModule.BlockchainWrapper?,
) {
    if (blockchainWrapper?.type != SyncErrorModule.BlockchainWrapper.Type.Bitcoin) return

    slideFromBottom(BtcBlockchainSettingsPage(blockchainWrapper.blockchain))
}
