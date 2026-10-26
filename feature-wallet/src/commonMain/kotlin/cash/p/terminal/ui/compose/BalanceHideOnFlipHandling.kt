package cash.p.terminal.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.StateFlow
import org.koin.compose.koinInject

/** Hides the balance when the device is flipped face down, only while some screen allows it. */
interface BalanceHideOnFlipHandler {
    /** Set once a flip has hidden the balance, until the user has seen the explanation. */
    val pendingInfo: StateFlow<Boolean>

    fun setHandlingAllowed(owner: Any, allowed: Boolean)
    fun consumeInfo()
    fun suppressInfo()
}

@Composable
fun BalanceHideOnFlipHandling(allowed: Boolean = true) {
    val handler = koinInject<BalanceHideOnFlipHandler>()
    val owner = remember { Any() }

    DisposableEffect(handler, allowed) {
        handler.setHandlingAllowed(owner, allowed)
        onDispose { handler.setHandlingAllowed(owner, false) }
    }
}
