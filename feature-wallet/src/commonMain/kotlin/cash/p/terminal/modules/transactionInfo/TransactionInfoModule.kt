package cash.p.terminal.modules.transactionInfo

import cash.p.terminal.core.managers.TransactionAdapterManager
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.koin.core.scope.Scope
import cash.p.terminal.core.adapters.EvmTransactionsAdapter
import io.horizontalsystems.core.entities.CurrencyValue
import cash.p.terminal.entities.LastBlockInfo
import cash.p.terminal.entities.nft.NftAssetBriefMetadata
import cash.p.terminal.entities.nft.NftUid
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.modules.offline.walletFor
import cash.p.terminal.modules.transactions.AmlStatus
import cash.p.terminal.modules.transactions.poison_status.PoisonStatus
import cash.p.terminal.modules.transactions.NftMetadataService
import cash.p.terminal.modules.transactions.TransactionItem
import cash.p.terminal.modules.transactions.TransactionStatus
import cash.p.terminal.network.changenow.domain.entity.TransactionStatusEnum
import cash.p.terminal.network.swaprepository.SwapProvider
import cash.p.terminal.ui_compose.ColoredValue
import cash.p.terminal.wallet.IWalletManager
import io.horizontalsystems.core.entities.BlockchainType
import java.math.BigDecimal
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Transactions_Completed
import cash.p.terminal.resources.Transactions_Failed
import cash.p.terminal.resources.Transactions_Pending
import cash.p.terminal.resources.Transactions_Processing
import cash.p.terminal.resources.ic_language

object TransactionInfoModule {

    data class ExplorerData(
        val title: String,
        val url: String?,
        val useDirectTitle: Boolean = false,
        val iconResId: DrawableResource = Res.drawable.ic_language,
    )
}

sealed class TransactionStatusViewItem(val name: StringResource) {
    object Pending : TransactionStatusViewItem(Res.string.Transactions_Pending)

    //progress in 0.0 .. 1.0
    class Processing(val progress: Float) : TransactionStatusViewItem(Res.string.Transactions_Processing)
    object Completed : TransactionStatusViewItem(Res.string.Transactions_Completed)
    object Failed : TransactionStatusViewItem(Res.string.Transactions_Failed)
}

data class TransactionInfoItem(
    val record: TransactionRecord,
    val externalStatus: TransactionStatus?,
    val lastBlockInfo: LastBlockInfo?,
    val explorerData: List<TransactionInfoModule.ExplorerData>,
    val rates: Map<String, CurrencyValue>,
    val nftMetadata: Map<NftUid, NftAssetBriefMetadata>,
    val hideAmount: Boolean,
    val transactionStatusUrl: Pair<String, String>? = null,
    // Swap provider amount fields
    val swapAmountOut: BigDecimal? = null,
    val swapAmountOutReal: BigDecimal? = null,
    val swapAmountIn: BigDecimal? = null,
    val swapCoinCodeOut: String? = null,
    val swapCoinCodeIn: String? = null,
    val swapCoinUidOut: String? = null,
    val swapCoinUidIn: String? = null,
    // AML status
    val amlStatus: AmlStatus? = null,
    val swapProvider: SwapProvider? = null,
    // Unstoppable sub-provider api id, so the details screen shows the concrete provider (e.g. Near)
    // instead of the aggregator name.
    val swapSubProviderId: String? = null,
    val swapTransactionId: String? = null,
    val swapTransactionStatus: TransactionStatusEnum? = null,
    val poisonStatus: PoisonStatus = PoisonStatus.BLOCKCHAIN,
    val offlineStatus: ColoredValue? = null,
    // Record equality identifies the transaction, not changes to its status or details.
    val recordRevision: Long = 0,
)

val BlockchainType.resendable: Boolean
    get() =
        when (this) {
            BlockchainType.Optimism,
            BlockchainType.Base,
            BlockchainType.ZkSync,
            BlockchainType.RobinhoodChain,
            BlockchainType.ArbitrumOne -> false
            else -> true
        }

fun Scope.transactionInfoViewModel(transactionItem: TransactionItem): TransactionInfoViewModel {
    val transactionSource = transactionItem.record.source
    val adapter = get<TransactionAdapterManager>().getAdapter(transactionSource)
    val service = TransactionInfoService(
        initialTransactionRecord = transactionItem.record,
        userSwapTransactionId = transactionItem.changeNowTransactionId,
        walletUid = transactionItem.walletUid,
        adapter = adapter,
        marketKit = get(),
        currencyManager = get(),
        nftMetadataService = NftMetadataService(get()),
        updateSwapProviderTransactionsStatusUseCase = get(),
        swapProviderTransactionsStorage = get(),
        dispatcherProvider = get(),
        transactionStatusUrl = transactionItem.transactionStatusUrl,
        offlineStatus = transactionItem.offlineStatus,
    )
    val factory = TransactionInfoViewItemFactory(
        offlineOperationGate = get(),
        wallet = get<IWalletManager>().walletFor(transactionSource.blockchain.type),
        blockchainType = transactionSource.blockchain.type,
        evmKitWrapper = (adapter as? EvmTransactionsAdapter)?.evmKitWrapper,
        platformOperationSupport = get(),
        unstoppableProviderTitles = get(),
    )

    return TransactionInfoViewModel(
        service = service,
        factory = factory,
        contactsRepository = get(),
        balanceHiddenManager = get(),
        pendingTransactionRepository = get(),
        poisonAddressManager = get(),
        addressLabelManager = get(),
    )
}
