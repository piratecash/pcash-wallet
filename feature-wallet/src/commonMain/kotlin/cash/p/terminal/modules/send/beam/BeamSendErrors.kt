package cash.p.terminal.modules.send.beam

import cash.p.terminal.core.managers.BeamSendCoordinator.Reason
import cash.p.terminal.core.managers.BeamSendCoordinator.SendException
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Swap_ErrorInsufficientBalance
import cash.p.terminal.resources.beam_offline_context_unavailable
import cash.p.terminal.resources.beam_send_not_ready
import cash.p.terminal.resources.beam_send_preview_error
import cash.p.terminal.resources.beam_send_quote_changed
import cash.p.terminal.resources.beam_send_regular_online_only
import cash.p.terminal.resources.beam_send_uncertain
import org.jetbrains.compose.resources.StringResource

/** [attempted]: a durable operation may exist, so an unexplained failure must not read as "nothing was sent". */
internal fun beamSendErrorMessage(error: Throwable, attempted: Boolean): StringResource =
    when ((error as? SendException)?.reason) {
        Reason.ContextUnavailable -> Res.string.beam_offline_context_unavailable
        Reason.InsufficientFunds -> Res.string.Swap_ErrorInsufficientBalance
        Reason.NotReady, Reason.SessionMismatch -> Res.string.beam_send_not_ready
        Reason.QuoteChanged -> Res.string.beam_send_quote_changed
        Reason.OnlineOnlyReceiver -> Res.string.beam_send_regular_online_only
        else -> if (attempted) Res.string.beam_send_uncertain else Res.string.beam_send_preview_error
    }
