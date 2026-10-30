package cash.p.terminal.modules.multiswap.action

import androidx.compose.runtime.Composable
import cash.p.terminal.navigation.ResultLauncher

interface ISwapProviderAction {
    val inProgress: Boolean

    @Composable
    fun getTitle() : String

    @Composable
    fun getTitleInProgress() : String

    @Composable
    fun getDescription() : String? = null

    /** Opens the action's page with itself as the launch argument, so the result comes back with it. */
    fun execute(launcher: ResultLauncher<ISwapProviderAction>)

    fun isCompleted(result: Any): Boolean
}
