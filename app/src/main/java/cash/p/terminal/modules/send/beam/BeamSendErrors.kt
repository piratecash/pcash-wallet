package cash.p.terminal.modules.send.beam

import cash.p.terminal.R
import cash.p.terminal.core.managers.BeamSendCoordinator.Reason
import cash.p.terminal.core.managers.BeamSendCoordinator.SendException

/** [attempted]: a durable operation may exist, so an unexplained failure must not read as "nothing was sent". */
internal fun beamSendErrorMessage(error: Throwable, attempted: Boolean): Int =
    when ((error as? SendException)?.reason) {
        Reason.ContextUnavailable -> R.string.beam_offline_context_unavailable
        Reason.InsufficientFunds -> R.string.Swap_ErrorInsufficientBalance
        Reason.NotReady, Reason.SessionMismatch -> R.string.beam_send_not_ready
        Reason.QuoteChanged -> R.string.beam_send_quote_changed
        else -> if (attempted) R.string.beam_send_uncertain else R.string.beam_send_preview_error
    }
