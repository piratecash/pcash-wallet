package cash.p.terminal.modules.multiswap.action

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import cash.p.terminal.R
import cash.p.terminal.modules.activatetoken.ActivateTokenFragment
import cash.p.terminal.navigation.slideFromBottomForResult
import cash.p.terminal.wallet.Wallet

class ActionActivateStellarAsset(
    private val wallet: Wallet,
    override val inProgress: Boolean = false,
) : ISwapProviderAction {

    @Composable
    override fun getTitle() = stringResource(R.string.Button_Activate)

    @Composable
    override fun getTitleInProgress() = stringResource(R.string.activate_activating)

    @Composable
    override fun getDescription() = stringResource(
        R.string.activation_required_dialog_description,
        wallet.coin.code,
        wallet.coin.code,
    )

    override fun execute(navController: NavController, onActionCompleted: () -> Unit) {
        navController.slideFromBottomForResult<ActivateTokenFragment.Result>(
            R.id.activateTokenFragment,
            wallet,
        ) {
            if (it.activated) onActionCompleted()
        }
    }
}
