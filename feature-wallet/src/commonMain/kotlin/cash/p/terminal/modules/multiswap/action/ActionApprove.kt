package cash.p.terminal.modules.multiswap.action

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import cash.p.terminal.modules.eip20approve.Eip20ApprovePage
import cash.p.terminal.navigation.ResultLauncher
import cash.p.terminal.wallet.Token
import java.math.BigDecimal
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Swap_Unlock
import cash.p.terminal.resources.Swap_Unlocking

class ActionApprove(
    private val requiredAllowance: BigDecimal,
    private val spenderAddress: String,
    private val tokenIn: Token,
    override val inProgress: Boolean
) : ISwapProviderAction {

    @Composable
    override fun getTitle() = stringResource(Res.string.Swap_Unlock)

    @Composable
    override fun getTitleInProgress() = stringResource(Res.string.Swap_Unlocking)

    override fun execute(launcher: ResultLauncher<ISwapProviderAction>) = launcher.slideFromBottom(page(), this)

    override fun isCompleted(result: Any) = true

    private fun page(): Eip20ApprovePage {
        val approveData = Eip20ApprovePage.Input(
            tokenIn,
            requiredAllowance,
            spenderAddress
        )
        return Eip20ApprovePage(approveData)
    }
}
