package cash.p.terminal.modules.multiswap.action

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cash.p.terminal.R
import cash.p.terminal.entities.CoinValue
import cash.p.terminal.modules.eip20revoke.Eip20RevokeConfirmPage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.wallet.Token
import java.math.BigDecimal

class ActionRevoke(
    private val token: Token,
    private val spenderAddress: String,
    override val inProgress: Boolean,
    private val allowance: BigDecimal
) : ISwapProviderAction {

    @Composable
    override fun getTitle() = stringResource(R.string.Swap_Revoke)

    @Composable
    override fun getTitleInProgress() = stringResource(R.string.Swap_Revoking)

    @Composable
    override fun getDescription() =
        stringResource(R.string.Approve_RevokeAndApproveInfo, CoinValue(token, allowance).getFormattedFull())

    override fun execute(navigation: HSNavigation, onActionCompleted: () -> Unit) {
        navigation.slideFromBottomForResult<Eip20RevokeConfirmPage.Result>(
            Eip20RevokeConfirmPage(Eip20RevokeConfirmPage.Input(token, spenderAddress, allowance))
        ) {
            onActionCompleted.invoke()
        }
    }
}
