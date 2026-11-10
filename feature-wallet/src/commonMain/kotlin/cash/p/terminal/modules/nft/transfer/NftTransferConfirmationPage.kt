package cash.p.terminal.modules.nft.transfer

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cash.p.terminal.entities.NftItemRecord
import cash.p.terminal.modules.nft.NftPreviewPage
import cash.p.terminal.modules.send.SendConfirmationScreen
import cash.p.terminal.modules.send.fee.NetworkFeeWarningOverlay
import cash.p.terminal.modules.send.rememberBalanceToggle
import cash.p.terminal.modules.send.rememberExistingViewModel
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpFrom
import java.math.BigDecimal

class NftTransferConfirmationPage(private val item: NftItemRecord) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel = navigation.rememberExistingViewModel(NftTransferPage::class, NftTransferViewModel::class)
        val data = remember { viewModel?.confirmationData() }
        if (viewModel == null || data == null) {
            LaunchedEffect(Unit) { navigation.navigateUpFrom(this@NftTransferConfirmationPage) }
            return
        }
        Confirmation(navigation, viewModel, data)
    }

    @Composable
    private fun Confirmation(
        navigation: HSNavigation,
        viewModel: NftTransferViewModel,
        data: NftTransferConfirmationData,
    ) {
        val token = viewModel.wallet.token
        val insufficientText = viewModel.uiState.insufficientBalance?.getString()
        SendConfirmationScreen(
            navigation = navigation,
            coinMaxAllowedDecimals = token.decimals,
            feeCoinMaxAllowedDecimals = token.decimals,
            rate = viewModel.rate,
            feeCoinRate = viewModel.rate,
            sendResult = viewModel.sendResult,
            blockchainType = token.blockchainType,
            coin = token.coin,
            feeCoin = token.coin,
            amount = BigDecimal.ZERO,
            address = data.address,
            contact = data.contact,
            fee = viewModel.getEstimatedFee() ?: data.fee,
            lockTimeInterval = null,
            memo = data.comment,
            rbfEnabled = null,
            onClickSend = viewModel::onClickSendWithWarningCheck,
            sendEntryPoint = NftPreviewPage::class,
            isSynced = viewModel.isSynced,
            hasAdapterError = viewModel.hasAdapterError,
            onRetrySync = viewModel::retryAdapterSync,
            sendEnabled = viewModel.canContinue,
            sendToken = token,
            feeToken = viewModel.feeToken,
            feeCoinBalance = viewModel.feeCoinBalance,
            displayBalance = viewModel.displayBalance,
            insufficientFeeBalance = insufficientText != null,
            balanceHidden = viewModel.balanceHidden,
            onBalanceClicked = rememberBalanceToggle(viewModel::toggleHideBalance),
            feeWarningData = viewModel.inlineFeeWarningData,
            insufficientFeeText = insufficientText,
            assetContent = { NftAssetRow(item, Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) },
            recipientDomain = data.address.domain,
        )

        NetworkFeeWarningOverlay(
            feeWarningData = viewModel.feeWarningData,
            // The balance can drop while the warning is open; close it instead of sending past the gate.
            onConfirm = if (viewModel.canContinue) {
                viewModel::onFeeWarningConfirmed
            } else {
                viewModel::onFeeWarningCancelled
            },
            onCancel = viewModel::onFeeWarningCancelled,
        )
    }
}
