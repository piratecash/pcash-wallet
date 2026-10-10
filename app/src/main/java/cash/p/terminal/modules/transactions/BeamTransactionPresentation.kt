package cash.p.terminal.modules.transactions

import cash.p.beam.BeamTransactionDirection
import cash.p.beam.BeamTransactionStatus
import cash.p.terminal.R
import cash.p.terminal.entities.transactionrecords.beam.BeamTransactionRecord
import cash.p.terminal.strings.helpers.Translator

internal val BeamTransactionRecord.directionTitle: Int
    get() = when (direction) {
        BeamTransactionDirection.Incoming -> R.string.Transactions_Receive
        BeamTransactionDirection.Outgoing, BeamTransactionDirection.Self -> R.string.Transactions_Send
    }

internal val BeamTransactionRecord.statusTitle: Int
    get() = when (sdkStatus) {
        BeamTransactionStatus.Pending -> R.string.Transactions_Pending
        BeamTransactionStatus.InProgress -> when (direction) {
            BeamTransactionDirection.Outgoing -> R.string.beam_history_waiting_for_receiver
            BeamTransactionDirection.Incoming -> R.string.beam_history_waiting_for_sender
            BeamTransactionDirection.Self -> R.string.beam_history_in_progress
        }
        BeamTransactionStatus.Registering -> R.string.beam_history_registering
        BeamTransactionStatus.Confirming -> R.string.transaction_swap_status_confirming
        BeamTransactionStatus.Completed -> R.string.Transactions_Completed
        BeamTransactionStatus.Failed -> if (failureReason == "TransactionExpired") {
            R.string.beam_history_expired
        } else {
            R.string.Transactions_Failed
        }
        BeamTransactionStatus.Canceled -> R.string.beam_history_canceled
        BeamTransactionStatus.Unknown -> R.string.transaction_swap_status_unknown
    }

internal val BeamTransactionRecord.failureReasonText: String?
    get() = when (failureReason) {
        null -> null
        "Canceled" -> Translator.getString(R.string.beam_history_canceled)
        "TransactionExpired" -> Translator.getString(R.string.beam_history_expired)
        "ExpiredAddressProvided" -> Translator.getString(R.string.beam_history_address_expired)
        "NoInputs" -> Translator.getString(R.string.Swap_ErrorInsufficientBalance)
        else -> failureReason
    }
