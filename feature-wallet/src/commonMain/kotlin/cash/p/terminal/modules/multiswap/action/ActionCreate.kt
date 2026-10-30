package cash.p.terminal.modules.multiswap.action

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import cash.p.terminal.navigation.ResultLauncher
import cash.p.terminal.wallet.Token
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.swap_create_wallets
import cash.p.terminal.resources.swap_creating_wallets
import org.jetbrains.compose.resources.StringResource

/***
 * Action to add tokens before action
 */
class ActionCreate(
    override val inProgress: Boolean,
    val descriptionResId: StringResource,
    val tokensToAdd: Set<Token>,
) : ISwapProviderAction {

    @Composable
    override fun getTitle() = stringResource(Res.string.swap_create_wallets)

    @Composable
    override fun getTitleInProgress() = stringResource(Res.string.swap_creating_wallets)

    @Composable
    override fun getDescription() = stringResource(descriptionResId)

    override fun execute(launcher: ResultLauncher<ISwapProviderAction>) = Unit

    override fun isCompleted(result: Any) = false
}
