package cash.p.terminal.modules.manageaccount.stellarsecretkey

import io.horizontalsystems.core.common.CommonParcelable
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import cash.p.terminal.modules.manageaccount.SecretKeyScreen
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import io.horizontalsystems.core.common.CommonParcelize
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.StellarSecretKey_ShowSecretKey
import cash.p.terminal.resources.StellarSecretKey_Title

class StellarSecretKeyPage(val input: Input) : HSPage(screenshotEnabled = false) {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        StellarSecretKeyScreen(navigation, input.stellarSecretKey)
    }

    @CommonParcelize
    data class Input(val stellarSecretKey: String) : CommonParcelable
}

@Composable
fun StellarSecretKeyScreen(
    navigation: HSNavigation,
    stellarSecretKey: String,
) {
    SecretKeyScreen(
        navigation = navigation,
        secretKey = stellarSecretKey,
        title = stringResource(Res.string.StellarSecretKey_Title),
        hideScreenText = stringResource(Res.string.StellarSecretKey_ShowSecretKey)
    )
}
