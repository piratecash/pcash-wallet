package cash.p.terminal.modules.transactions

import cash.p.terminal.core.managers.localizedEvmMethodLabel
import cash.p.terminal.entities.SwapProviderTransaction
import cash.p.terminal.entities.transactionrecords.PendingTransactionRecord
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.entities.transactionrecords.TransactionRecordType
import cash.p.terminal.entities.transactionrecords.beam.BeamTransactionRecord
import cash.p.terminal.entities.transactionrecords.bitcoin.BitcoinTransactionRecord
import cash.p.terminal.entities.transactionrecords.evm.EvmTransactionRecord
import cash.p.terminal.entities.transactionrecords.evm.TransferEvent
import cash.p.terminal.entities.transactionrecords.monero.MoneroTransactionRecord
import cash.p.terminal.entities.transactionrecords.solana.SolanaTransactionRecord
import cash.p.terminal.entities.transactionrecords.stellar.StellarTransactionRecord
import cash.p.terminal.entities.transactionrecords.thorchain.ThorchainTransactionRecord
import cash.p.terminal.entities.transactionrecords.ton.TonTransactionRecord
import cash.p.terminal.entities.transactionrecords.tron.TronTransactionRecord
import cash.p.terminal.network.changenow.domain.entity.TransactionStatusEnum
import cash.p.terminal.network.changenow.domain.entity.toStatus
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Transactions_Approve
import cash.p.terminal.resources.Transactions_Burn
import cash.p.terminal.resources.Transactions_ChangeTrust
import cash.p.terminal.resources.Transactions_ContractCall
import cash.p.terminal.resources.Transactions_ContractCreation
import cash.p.terminal.resources.Transactions_ContractDeploy
import cash.p.terminal.resources.Transactions_ExternalContractCall
import cash.p.terminal.resources.Transactions_Failed
import cash.p.terminal.resources.Transactions_Mint
import cash.p.terminal.resources.Transactions_Receive
import cash.p.terminal.resources.Transactions_Send
import cash.p.terminal.resources.Transactions_StellarTransaction
import cash.p.terminal.resources.Transactions_Swap
import cash.p.terminal.resources.Transactions_TonTransaction
import cash.p.terminal.resources.Transactions_Unknown
import cash.p.terminal.resources.transaction_pending_title
import cash.p.terminal.resources.transaction_swap_status_confirming
import cash.p.terminal.resources.transaction_swap_status_created_or_wait_user
import cash.p.terminal.resources.transaction_swap_status_exchanging
import cash.p.terminal.resources.transaction_swap_status_new
import cash.p.terminal.resources.transaction_swap_status_refunded
import cash.p.terminal.resources.transaction_swap_status_sending
import cash.p.terminal.resources.transaction_swap_status_unknown
import cash.p.terminal.resources.transaction_swap_status_verifying
import cash.p.terminal.resources.transaction_swap_status_waiting
import cash.p.terminal.resources.transactions_account_delegation
import cash.p.terminal.resources.transactions_account_delegation_revoke
import cash.p.terminal.resources.transactions_migrate
import cash.p.terminal.resources.transactions_swap_failed
import io.horizontalsystems.core.Translator
import org.jetbrains.compose.resources.StringResource

/** The title a list row shows for [this] record when it is not displayed as a provider swap. */
fun TransactionRecord.listTitle(): String = when (this) {
    is EvmTransactionRecord -> evmTitle()
    is TronTransactionRecord -> tronTitle()
    else -> Translator.getString(titleResource)
}

fun TransactionRecord.isSwap() = when (transactionRecordType) {
    TransactionRecordType.EVM_SWAP,
    TransactionRecordType.EVM_UNKNOWN_SWAP -> true

    else -> this is TonTransactionRecord &&
            actions.singleOrNull()?.type is TonTransactionRecord.Action.Type.Swap
}

/** A payout still confirming on chain shows a finished swap as confirming. */
fun SwapProviderTransaction.displayStatus(payoutConfirming: Boolean): TransactionStatusEnum {
    val providerStatus = status.toStatus()
    return if (payoutConfirming && providerStatus == TransactionStatusEnum.FINISHED) {
        TransactionStatusEnum.CONFIRMING
    } else {
        providerStatus
    }
}

fun TransactionStatusEnum.listTitle(): String = Translator.getString(
    when (this) {
        TransactionStatusEnum.NEW -> Res.string.transaction_swap_status_new
        TransactionStatusEnum.WAITING -> Res.string.transaction_swap_status_waiting
        TransactionStatusEnum.CONFIRMING -> Res.string.transaction_swap_status_confirming
        TransactionStatusEnum.EXCHANGING -> Res.string.transaction_swap_status_exchanging
        TransactionStatusEnum.SENDING -> Res.string.transaction_swap_status_sending
        TransactionStatusEnum.FINISHED -> Res.string.Transactions_Swap
        TransactionStatusEnum.FAILED -> Res.string.Transactions_Failed
        TransactionStatusEnum.REFUNDED -> Res.string.transaction_swap_status_refunded
        TransactionStatusEnum.VERIFYING -> Res.string.transaction_swap_status_verifying
        TransactionStatusEnum.CREATED_OR_WAIT_USER -> Res.string.transaction_swap_status_created_or_wait_user
        TransactionStatusEnum.UNKNOWN -> Res.string.transaction_swap_status_unknown
    }
)

private fun EvmTransactionRecord.evmTitle(): String {
    val resource = when (transactionRecordType) {
        TransactionRecordType.EVM_APPROVE -> Res.string.Transactions_Approve
        TransactionRecordType.EVM_CONTRACT_CALL ->
            return contractCallTitle(liquidityOperation?.let { Translator.getString(it.title) } ?: method)

        TransactionRecordType.EVM_EXTERNAL_CONTRACT_CALL -> externalContractCallTitle(incomingEvents, outgoingEvents)
        TransactionRecordType.EVM_CONTRACT_CREATION -> Res.string.Transactions_ContractCreation
        TransactionRecordType.EVM_INCOMING -> Res.string.Transactions_Receive
        TransactionRecordType.EVM_OUTGOING -> Res.string.Transactions_Send
        TransactionRecordType.EVM_SWAP,
        TransactionRecordType.EVM_UNKNOWN_SWAP -> swapTitle

        TransactionRecordType.EVM_ACCOUNT_DELEGATION -> if (delegationRevoked) {
            Res.string.transactions_account_delegation_revoke
        } else {
            Res.string.transactions_account_delegation
        }

        else -> Res.string.Transactions_Unknown
    }
    return Translator.getString(resource)
}

private fun TronTransactionRecord.tronTitle(): String {
    val resource = when (transactionRecordType) {
        TransactionRecordType.TRON_APPROVE -> Res.string.Transactions_Approve
        TransactionRecordType.TRON_CONTRACT_CALL -> return contractCallTitle(method)
        TransactionRecordType.TRON_EXTERNAL_CONTRACT_CALL -> externalContractCallTitle(incomingEvents, outgoingEvents)
        TransactionRecordType.TRON_INCOMING -> Res.string.Transactions_Receive
        TransactionRecordType.TRON_OUTGOING -> Res.string.Transactions_Send
        else -> return transaction.contract?.label ?: Translator.getString(Res.string.Transactions_Unknown)
    }
    return Translator.getString(resource)
}

private fun contractCallTitle(method: String?): String =
    method?.let(::localizedEvmMethodLabel) ?: Translator.getString(Res.string.Transactions_ContractCall)

private fun externalContractCallTitle(
    incomingEvents: List<TransferEvent>?,
    outgoingEvents: List<TransferEvent>?,
): StringResource {
    val (incomingValues, outgoingValues) =
        EvmTransactionRecord.combined(incomingEvents.orEmpty(), outgoingEvents.orEmpty())
    return when {
        outgoingValues.isEmpty() -> Res.string.Transactions_Receive
        incomingValues.isEmpty() -> Res.string.Transactions_Send
        else -> Res.string.Transactions_ExternalContractCall
    }
}

private val TransactionRecord.swapTitle: StringResource
    get() = if (failed) Res.string.transactions_swap_failed else Res.string.Transactions_Swap

private val TransactionRecord.titleResource: StringResource
    get() = when (this) {
        is BitcoinTransactionRecord -> when {
            transactionRecordType == TransactionRecordType.BITCOIN_INCOMING -> Res.string.Transactions_Receive
            isIronwoodMigration -> Res.string.transactions_migrate
            else -> Res.string.Transactions_Send
        }

        is SolanaTransactionRecord -> when (transactionRecordType) {
            TransactionRecordType.SOLANA_INCOMING -> Res.string.Transactions_Receive
            TransactionRecordType.SOLANA_OUTGOING -> Res.string.Transactions_Send
            else -> Res.string.Transactions_Unknown
        }

        is TonTransactionRecord -> liquidityOperation?.title ?: tonActionTitle
        is StellarTransactionRecord -> when (type) {
            is StellarTransactionRecord.Type.Send -> Res.string.Transactions_Send
            is StellarTransactionRecord.Type.Receive -> Res.string.Transactions_Receive
            is StellarTransactionRecord.Type.ChangeTrust -> Res.string.Transactions_ChangeTrust
            is StellarTransactionRecord.Type.Swap -> swapTitle
            is StellarTransactionRecord.Type.Unsupported -> Res.string.Transactions_StellarTransaction
        }

        is ThorchainTransactionRecord -> when (type) {
            is ThorchainTransactionRecord.Type.Incoming -> Res.string.Transactions_Receive
            is ThorchainTransactionRecord.Type.Outgoing -> Res.string.Transactions_Send
        }

        is BeamTransactionRecord -> directionTitle
        is MoneroTransactionRecord -> when (transactionRecordType) {
            TransactionRecordType.MONERO_INCOMING -> Res.string.Transactions_Receive
            TransactionRecordType.MONERO_OUTGOING -> Res.string.Transactions_Send
            else -> Res.string.Transactions_Unknown
        }

        is PendingTransactionRecord -> Res.string.transaction_pending_title
        else -> Res.string.Transactions_Unknown
    }

private val TonTransactionRecord.tonActionTitle: StringResource
    get() = when (actions.singleOrNull()?.type) {
        is TonTransactionRecord.Action.Type.Send -> Res.string.Transactions_Send
        is TonTransactionRecord.Action.Type.Receive -> Res.string.Transactions_Receive
        is TonTransactionRecord.Action.Type.Burn -> Res.string.Transactions_Burn
        is TonTransactionRecord.Action.Type.ContractCall -> Res.string.Transactions_ContractCall
        is TonTransactionRecord.Action.Type.ContractDeploy -> Res.string.Transactions_ContractDeploy
        is TonTransactionRecord.Action.Type.Mint -> Res.string.Transactions_Mint
        is TonTransactionRecord.Action.Type.Swap -> swapTitle
        is TonTransactionRecord.Action.Type.Unsupported,
        is TonTransactionRecord.Action.Type.LiquidityDeposit,
        null -> Res.string.Transactions_TonTransaction
    }
