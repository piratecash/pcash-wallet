package cash.p.terminal.modules.multiswap.action

import androidx.compose.runtime.Composable
import cash.p.terminal.navigation.HSNavigation

interface ISwapProviderAction {
    val inProgress: Boolean

    @Composable
    fun getTitle() : String

    @Composable
    fun getTitleInProgress() : String

    @Composable
    fun getDescription() : String? = null

    fun execute(navigation: HSNavigation, onActionCompleted: () -> Unit)
}
