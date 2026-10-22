package cash.p.terminal.modules.transactions

import cash.p.beam.BeamTransactionDirection
import cash.p.beam.BeamTransactionStatus
import cash.p.terminal.entities.transactionrecords.beam.BeamTransactionRecord
import io.horizontalsystems.core.Translator
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Swap_ErrorInsufficientBalance
import cash.p.terminal.resources.Transactions_Completed
import cash.p.terminal.resources.Transactions_Failed
import cash.p.terminal.resources.Transactions_Pending
import cash.p.terminal.resources.Transactions_Receive
import cash.p.terminal.resources.Transactions_Send
import cash.p.terminal.resources.beam_history_address_expired
import cash.p.terminal.resources.beam_history_canceled
import cash.p.terminal.resources.beam_history_expired
import cash.p.terminal.resources.beam_history_in_progress
import cash.p.terminal.resources.beam_history_registering
import cash.p.terminal.resources.beam_history_waiting_for_receiver
import cash.p.terminal.resources.beam_history_waiting_for_sender
import cash.p.terminal.resources.transaction_swap_status_confirming
import cash.p.terminal.resources.transaction_swap_status_unknown
import org.jetbrains.compose.resources.StringResource

internal val BeamTransactionRecord.directionTitle: StringResource
    get() = when (direction) {
        BeamTransactionDirection.Incoming -> Res.string.Transactions_Receive
        BeamTransactionDirection.Outgoing, BeamTransactionDirection.Self -> Res.string.Transactions_Send
    }

internal val BeamTransactionRecord.statusTitle: StringResource
    get() = when (sdkStatus) {
        BeamTransactionStatus.Pending -> Res.string.Transactions_Pending
        BeamTransactionStatus.InProgress -> when (direction) {
            BeamTransactionDirection.Outgoing -> Res.string.beam_history_waiting_for_receiver
            BeamTransactionDirection.Incoming -> Res.string.beam_history_waiting_for_sender
            BeamTransactionDirection.Self -> Res.string.beam_history_in_progress
        }
        BeamTransactionStatus.Registering -> Res.string.beam_history_registering
        BeamTransactionStatus.Confirming -> Res.string.transaction_swap_status_confirming
        BeamTransactionStatus.Completed -> Res.string.Transactions_Completed
        BeamTransactionStatus.Failed -> if (failureReason == "TransactionExpired") {
            Res.string.beam_history_expired
        } else {
            Res.string.Transactions_Failed
        }
        BeamTransactionStatus.Canceled -> Res.string.beam_history_canceled
        BeamTransactionStatus.Unknown -> Res.string.transaction_swap_status_unknown
    }

internal val BeamTransactionRecord.failureReasonText: String?
    get() = when (failureReason) {
        null -> null
        "Canceled" -> Translator.getString(Res.string.beam_history_canceled)
        "TransactionExpired" -> Translator.getString(Res.string.beam_history_expired)
        "ExpiredAddressProvided" -> Translator.getString(Res.string.beam_history_address_expired)
        "NoInputs" -> Translator.getString(Res.string.Swap_ErrorInsufficientBalance)
        else -> failureReason
    }
