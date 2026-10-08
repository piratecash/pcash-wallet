package cash.p.terminal.modules.multiswap.action

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cash.p.terminal.R
import cash.p.terminal.modules.eip20approve.Eip20ApproveConfirmPage
import cash.p.terminal.modules.eip20approve.Eip20ApprovePage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.wallet.Token
import java.math.BigDecimal

class ActionApprove(
    private val requiredAllowance: BigDecimal,
    private val spenderAddress: String,
    private val tokenIn: Token,
    override val inProgress: Boolean
) : ISwapProviderAction {

    @Composable
    override fun getTitle() = stringResource(R.string.Swap_Unlock)

    @Composable
    override fun getTitleInProgress() = stringResource(R.string.Swap_Unlocking)

    override fun execute(navigation: HSNavigation, onActionCompleted: () -> Unit) {
        val approveData = Eip20ApprovePage.Input(
            tokenIn,
            requiredAllowance,
            spenderAddress
        )

        navigation.slideFromBottomForResult<Eip20ApproveConfirmPage.Result>(
            Eip20ApprovePage(approveData)
        ) {
            onActionCompleted.invoke()
        }
    }
}
