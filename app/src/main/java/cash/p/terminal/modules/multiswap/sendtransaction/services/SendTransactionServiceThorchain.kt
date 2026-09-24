package cash.p.terminal.modules.multiswap.sendtransaction.services

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import cash.p.terminal.core.adapters.thorchain.ThorchainAdapter
import cash.p.terminal.core.ethereum.CautionViewItem
import cash.p.terminal.entities.CoinValue
import cash.p.terminal.modules.multiswap.sendtransaction.ISendTransactionService
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionData
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionResult
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionServiceState
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionSettings
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.getMaxSendableBalance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

class SendTransactionServiceThorchain(token: Token) : ISendTransactionService<ThorchainAdapter>(token) {

    private var sendData: SendTransactionData.Thorchain? = null
    private var cautions: List<CautionViewItem> = emptyList()

    override val sendTransactionSettingsFlow: StateFlow<SendTransactionSettings> =
        MutableStateFlow(SendTransactionSettings.Common)

    // The fee is fetched asynchronously and balances move: re-check what setSendTransactionData checked.
    override fun start(coroutineScope: CoroutineScope) {
        coroutineScope.launch {
            merge(adapter.sendFeeUpdatedFlow, adapter.balanceUpdatedFlow, adapter.nativeBalanceUpdatedFlow)
                .collect { sendData?.let(::updateCautions) }
        }
    }

    override fun hasSettings() = false

    @Composable
    override fun GetSettingsContent(navController: NavController) = Unit

    override suspend fun setSendTransactionData(data: SendTransactionData) {
        check(data is SendTransactionData.Thorchain)
        sendData = data
        updateCautions(data)
    }

    private fun updateCautions(data: SendTransactionData.Thorchain) {
        cautions = listOfNotNull(adapter.feeShortfall(data.amount)?.let { createCaution(it) })
        emitState()
    }

    override suspend fun sendTransaction(mevProtectionEnabled: Boolean): SendTransactionResult {
        val txHash = when (val data = checkNotNull(sendData) { "Transaction data not set" }) {
            is SendTransactionData.Thorchain.Deposit -> adapter.deposit(data.asset, data.amount, data.memo)
            is SendTransactionData.Thorchain.Send -> adapter.send(data.amount, data.address, data.memo)
        }
        markTransactionCreated(txHash)
        return SendTransactionResult.Thorchain(txHash, adapter.recordUid(txHash))
    }

    override fun createState() = SendTransactionServiceState(
        availableBalance = adapterManager.getMaxSendableBalance(wallet, adapter.maxSpendableBalance),
        networkFee = getAmountData(CoinValue(feeToken, adapter.sendFee)),
        cautions = cautions,
        sendable = sendData != null && cautions.isEmpty(),
        loading = sendData == null,
        fields = emptyList(),
    )
}
