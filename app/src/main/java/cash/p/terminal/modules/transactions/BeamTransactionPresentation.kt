package cash.p.terminal.modules.transactions

import cash.p.beam.BeamTransactionDirection
import cash.p.beam.BeamTransactionStatus
import cash.p.terminal.R
import cash.p.terminal.entities.transactionrecords.beam.BeamTransactionRecord

internal val BeamTransactionRecord.directionTitle: Int
    get() = when (direction) {
        BeamTransactionDirection.Incoming -> R.string.Transactions_Receive
        BeamTransactionDirection.Outgoing, BeamTransactionDirection.Self -> R.string.Transactions_Send
    }

internal val BeamTransactionRecord.statusTitle: Int
    get() = when (sdkStatus) {
        BeamTransactionStatus.Pending -> R.string.Transactions_Pending
        BeamTransactionStatus.InProgress -> R.string.beam_history_in_progress
        BeamTransactionStatus.Registering -> R.string.beam_history_registering
        BeamTransactionStatus.Confirming -> R.string.transaction_swap_status_confirming
        BeamTransactionStatus.Completed -> R.string.Transactions_Completed
        BeamTransactionStatus.Failed -> R.string.Transactions_Failed
        BeamTransactionStatus.Canceled -> R.string.beam_history_canceled
        BeamTransactionStatus.Unknown -> R.string.transaction_swap_status_unknown
    }
