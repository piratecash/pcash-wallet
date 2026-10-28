package cash.p.terminal.modules.send.evm.confirmation

import androidx.lifecycle.viewModelScope
import io.horizontalsystems.core.ViewModelUiState
import cash.p.terminal.core.ethereum.CautionViewItem
import cash.p.terminal.core.ethereum.EvmCoinServiceFactory
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionData
import cash.p.terminal.modules.multiswap.sendtransaction.services.SendTransactionServiceEvm
import cash.p.terminal.modules.multiswap.ui.DataField
import cash.p.terminal.modules.send.SendModule
import cash.p.terminal.modules.send.evm.SendEvmData
import cash.p.terminal.modules.sendevmtransaction.SectionViewItem
import cash.p.terminal.modules.sendevmtransaction.SendEvmTransactionViewItemFactory
import io.horizontalsystems.ethereumkit.models.TransactionData
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import cash.p.terminal.core.managers.EvmBlockchainManager
import org.koin.core.scope.Scope

internal class SendEvmConfirmationViewModel(
    sendEvmTransactionViewItemFactory: SendEvmTransactionViewItemFactory,
    val sendTransactionService: SendTransactionServiceEvm,
    transactionData: TransactionData,
    additionalInfo: SendEvmData.AdditionalInfo?
) : ViewModelUiState<SendEvmConfirmationUiState>() {
    private var sendTransactionState = sendTransactionService.stateFlow.value

    private val sectionViewItems = sendEvmTransactionViewItemFactory.getItems(
        transactionData,
        additionalInfo,
        sendTransactionService.decorate(transactionData)
    )

    init {
        viewModelScope.launch {
            sendTransactionService.stateFlow.collect { transactionState ->
                sendTransactionState = transactionState
                emitState()
            }
        }

        sendTransactionService.start(viewModelScope)

        viewModelScope.launch {
            sendTransactionService.setSendTransactionData(
                SendTransactionData.Evm(
                    transactionData,
                    null
                )
            )
        }
    }

    override fun createState() = SendEvmConfirmationUiState(
        networkFee = sendTransactionState.networkFee,
        cautions = sendTransactionState.cautions,
        sendEnabled = sendTransactionState.sendable,
        transactionFields = sendTransactionState.fields,
        sectionViewItems = sectionViewItems
    )

    suspend fun send() = withContext(Dispatchers.Default) {
        sendTransactionService.send()
    }
}

internal fun Scope.sendEvmConfirmationViewModel(
    transactionData: TransactionData,
    additionalInfo: SendEvmData.AdditionalInfo?,
    blockchainType: BlockchainType
): SendEvmConfirmationViewModel {
    val token = requireNotNull(get<EvmBlockchainManager>().getBaseToken(blockchainType))
    val sendTransactionService = SendTransactionServiceEvm(token, ignoreMevErrors = true)
    val feeToken = requireNotNull(get<EvmBlockchainManager>().getBaseToken(blockchainType))
    val coinServiceFactory = EvmCoinServiceFactory(feeToken, get(), get(), get())

    val sendEvmTransactionViewItemFactory = SendEvmTransactionViewItemFactory(
        get(),
        coinServiceFactory,
        get(),
        blockchainType
    )

    return SendEvmConfirmationViewModel(
        sendEvmTransactionViewItemFactory,
        sendTransactionService,
        transactionData,
        additionalInfo
    )
}

data class SendEvmConfirmationUiState(
    val networkFee: SendModule.AmountData?,
    val cautions: List<CautionViewItem>,
    val sendEnabled: Boolean,
    val transactionFields: List<DataField>,
    val sectionViewItems: List<SectionViewItem>
)
