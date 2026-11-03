package cash.p.terminal.modules.multiswap.ui

import androidx.compose.runtime.Composable
import cash.p.terminal.navigation.HSNavigation

interface DataField {
    @Composable
    fun GetContent(navigation: HSNavigation, borderTop: Boolean)
}
