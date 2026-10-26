package cash.p.terminal.shared.desktop

import cash.p.terminal.ui.compose.BalanceHideOnFlipHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A desktop has no flip sensor. */
object DesktopBalanceHideOnFlipHandler : BalanceHideOnFlipHandler {
    override val pendingInfo: StateFlow<Boolean> = MutableStateFlow(false)

    override fun setHandlingAllowed(owner: Any, allowed: Boolean) = Unit
    override fun consumeInfo() = Unit
    override fun suppressInfo() = Unit
}
