package cash.p.terminal.ui_compose.components

import androidx.compose.runtime.Composable

@Composable
actual fun rememberTextShare(): TextShare = NoTextShare

private object NoTextShare : TextShare {
    override val isAvailable = false
    override fun share(text: String) = Unit
}
