package cash.p.terminal.modules.transactionInfo.options

import androidx.lifecycle.viewModelScope
import io.horizontalsystems.core.Translator
import io.horizontalsystems.core.ViewModelUiState
import cash.p.terminal.core.ethereum.EvmCoinServiceFactory
import cash.p.terminal.core.managers.EvmBlockchainManager
import cash.p.terminal.core.managers.EvmKitWrapper
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionData
import cash.p.terminal.modules.multiswap.sendtransaction.services.SendTransactionServiceEvm
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionServiceState
import cash.p.terminal.modules.offline.OfflineOperationGate
import cash.p.terminal.modules.offline.OperationAvailability
import cash.p.terminal.modules.offline.availabilityFor
import cash.p.terminal.modules.offline.walletFor
import cash.p.terminal.modules.sendevmtransaction.SectionViewItem
import cash.p.terminal.modules.sendevmtransaction.SendEvmTransactionViewItemFactory
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.ethereumkit.core.hexStringToByteArray
import io.horizontalsystems.ethereumkit.models.TransactionData
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.scope.Scope
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.TransactionInfoOptions_SpeedUp_Button
import cash.p.terminal.resources.TransactionInfoOptions_SpeedUp_Title

// import java.math.BigInteger // MOBILE-593

internal class TransactionSpeedUpCancelViewModel(
    val sendTransactionService: SendTransactionServiceEvm,
    private val transactionHash: String,
    private val evmKitWrapper: EvmKitWrapper,
    private val optionType: SpeedUpCancelType,
    private val sendEvmTransactionViewItemFactory: SendEvmTransactionViewItemFactory,
    private val offlineOperationGate: OfflineOperationGate,
    walletManager: IWalletManager,
) : ViewModelUiState<TransactionSpeedUpCancelUiState>() {

    val wallet: Wallet? = walletManager.walletFor(evmKitWrapper.blockchainType)

    val title: String = when (optionType) {
        SpeedUpCancelType.SpeedUp -> Translator.getString(
            Res.string.TransactionInfoOptions_SpeedUp_Title
        )
        // SpeedUpCancelType.Cancel -> Translator.getString(Res.string.TransactionInfoOptions_Cancel_Title) // MOBILE-593
    }

    val buttonTitle: String = when (optionType) {
        SpeedUpCancelType.SpeedUp -> Translator.getString(
            Res.string.TransactionInfoOptions_SpeedUp_Button
        )
        // SpeedUpCancelType.Cancel -> Translator.getString(Res.string.TransactionInfoOptions_Cancel_Button) // MOBILE-593
    }

    private var sendTransactionState: SendTransactionServiceState = sendTransactionService.stateFlow.value
    private var error: Throwable? = null
    private var sectionViewItems: List<SectionViewItem> = listOf()

    override fun createState() = TransactionSpeedUpCancelUiState(
        sendTransactionState = sendTransactionState,
        sectionViewItems = sectionViewItems,
        error = error,
        sendAvailability = offlineOperationGate.availabilityFor(
            wallet,
            error == null && sendTransactionState.sendable
        )
    )

    init {
        viewModelScope.launch {
            val fullTransaction = evmKitWrapper.evmKit
                .getFullTransactions(listOf(transactionHash.hexStringToByteArray()))
                .first()

            fullTransaction.transaction.nonce?.let {
                sendTransactionService.fixNonce(it)
            }

            if (fullTransaction.transaction.blockNumber != null) {
                error = TransactionAlreadyInBlock()

                emitState()
            } else {
                val transactionData = when (optionType) {
                    SpeedUpCancelType.SpeedUp -> {
                        val transaction = fullTransaction.transaction
                        TransactionData(transaction.to!!, transaction.value!!, transaction.input!!)
                    }

                    // MOBILE-593
                    /*SpeedUpCancelType.Cancel -> {
                        TransactionData(
                            evmKitWrapper.evmKit.receiveAddress,
                            BigInteger.ZERO,
                            byteArrayOf()
                        )
                    }*/
                }

                sectionViewItems = sendEvmTransactionViewItemFactory.getItems(
                    transactionData,
                    null,
                    sendTransactionService.decorate(transactionData)
                )
                emitState()

                launch {
                    sendTransactionService.stateFlow.collect { transactionState ->
                        sendTransactionState = transactionState
                        emitState()
                    }
                }

                sendTransactionService.start(this)
                launch {
                    sendTransactionService.setSendTransactionData(
                        SendTransactionData.Evm(
                            transactionData,
                            null
                        )
                    )
                }
            }
        }
    }

    suspend fun send() = withContext(Dispatchers.Default) {
        sendTransactionService.send()
    }
}

data class TransactionSpeedUpCancelUiState(
    val sendTransactionState: SendTransactionServiceState,
    val sectionViewItems: List<SectionViewItem>,
    val error: Throwable?,
    val sendAvailability: OperationAvailability
)

class TransactionAlreadyInBlock : Exception()

internal fun Scope.transactionSpeedUpCancelViewModel(
    blockchainType: BlockchainType,
    transactionHash: String,
    optionType: SpeedUpCancelType,
    evmKitWrapper: EvmKitWrapper,
): TransactionSpeedUpCancelViewModel {
    val feeToken = get<EvmBlockchainManager>().getBaseToken(blockchainType)!!
    val sendTransactionService = SendTransactionServiceEvm(feeToken, evmKitWrapper = evmKitWrapper)
    val coinServiceFactory = EvmCoinServiceFactory(feeToken, get(), get(), get())

    val sendEvmTransactionViewItemFactory = SendEvmTransactionViewItemFactory(
        get(),
        coinServiceFactory,
        get(),
        blockchainType
    )

    return TransactionSpeedUpCancelViewModel(
        sendTransactionService,
        transactionHash,
        evmKitWrapper,
        optionType,
        sendEvmTransactionViewItemFactory,
        get(),
        get()
    )
}
