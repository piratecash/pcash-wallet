package cash.p.terminal.modules.multiswap.action

import cash.p.terminal.feature.wallet.ActivateTokenResult
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import cash.p.terminal.modules.activatetoken.ActivateTokenPage
import cash.p.terminal.navigation.ResultLauncher
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Button_Activate
import cash.p.terminal.resources.activate_activating
import cash.p.terminal.resources.activation_required_dialog_description

class ActionActivateStellarAsset(
    private val wallet: Wallet,
    override val inProgress: Boolean = false,
) : ISwapProviderAction {

    @Composable
    override fun getTitle() = stringResource(Res.string.Button_Activate)

    @Composable
    override fun getTitleInProgress() = stringResource(Res.string.activate_activating)

    @Composable
    override fun getDescription() = stringResource(
        Res.string.activation_required_dialog_description,
        wallet.coin.code,
        wallet.coin.code,
    )

    override fun execute(launcher: ResultLauncher<ISwapProviderAction>) = launcher.slideFromBottom(page(), this)

    override fun isCompleted(result: Any) = (result as? ActivateTokenResult)?.activated == true

    private fun page() = ActivateTokenPage(wallet)
}
