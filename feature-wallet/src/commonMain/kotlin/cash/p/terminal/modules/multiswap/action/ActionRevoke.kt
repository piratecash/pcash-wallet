package cash.p.terminal.modules.multiswap.action

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import cash.p.terminal.entities.CoinValue
import cash.p.terminal.modules.eip20revoke.Eip20RevokeConfirmPage
import cash.p.terminal.navigation.ResultLauncher
import cash.p.terminal.wallet.Token
import java.math.BigDecimal
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Approve_RevokeAndApproveInfo
import cash.p.terminal.resources.Swap_Revoke
import cash.p.terminal.resources.Swap_Revoking

class ActionRevoke(
    private val token: Token,
    private val spenderAddress: String,
    override val inProgress: Boolean,
    private val allowance: BigDecimal
) : ISwapProviderAction {

    @Composable
    override fun getTitle() = stringResource(Res.string.Swap_Revoke)

    @Composable
    override fun getTitleInProgress() = stringResource(Res.string.Swap_Revoking)

    @Composable
    override fun getDescription() =
        stringResource(Res.string.Approve_RevokeAndApproveInfo, CoinValue(token, allowance).getFormattedFull())

    override fun execute(launcher: ResultLauncher<ISwapProviderAction>) = launcher.slideFromBottom(page(), this)

    override fun isCompleted(result: Any) = true

    private fun page() = Eip20RevokeConfirmPage(Eip20RevokeConfirmPage.Input(token, spenderAddress, allowance))
}
