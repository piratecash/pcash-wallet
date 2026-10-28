package cash.p.terminal.modules.paycore

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import cash.p.terminal.navigation.ResultLauncher
import cash.p.terminal.modules.multiswap.action.ISwapProviderAction
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.paycore_verify_phone
import cash.p.terminal.resources.paycore_verifying

class PayCoreVerificationAction(
    override val inProgress: Boolean = false
) : ISwapProviderAction {

    @Composable
    override fun getTitle(): String = stringResource(Res.string.paycore_verify_phone)

    @Composable
    override fun getTitleInProgress(): String = stringResource(Res.string.paycore_verifying)

    override fun execute(launcher: ResultLauncher<ISwapProviderAction>) = Unit

    override fun isCompleted(result: Any) = false
}
