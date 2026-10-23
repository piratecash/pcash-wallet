package cash.p.terminal.modules.multiswap.action

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cash.p.terminal.R
import cash.p.terminal.modules.activatetoken.ActivateTokenPage
import cash.p.terminal.navigation.ResultLauncher
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

    override fun execute(launcher: ResultLauncher<ISwapProviderAction>) = launcher.slideFromBottom(page(), this)

    override fun isCompleted(result: Any) = (result as? ActivateTokenPage.Result)?.activated == true

    private fun page() = ActivateTokenPage(wallet)
}
