package cash.p.terminal.ui_compose.components

import androidx.compose.runtime.Composable

/** The system share sheet; Share buttons are hidden where [isAvailable] is false. */
interface TextShare {
    val isAvailable: Boolean
    fun share(text: String)
}

@Composable
expect fun rememberTextShare(): TextShare
