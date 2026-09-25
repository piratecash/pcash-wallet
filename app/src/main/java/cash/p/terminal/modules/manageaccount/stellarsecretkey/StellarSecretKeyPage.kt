package cash.p.terminal.modules.manageaccount.stellarsecretkey

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cash.p.terminal.R
import cash.p.terminal.modules.manageaccount.SecretKeyScreen
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import kotlinx.parcelize.Parcelize

class StellarSecretKeyPage(val input: Input) : HSPage(screenshotEnabled = false) {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        StellarSecretKeyScreen(navigation, input.stellarSecretKey)
    }

    @Parcelize
    data class Input(val stellarSecretKey: String) : Parcelable
}

@Composable
fun StellarSecretKeyScreen(
    navigation: HSNavigation,
    stellarSecretKey: String,
) {
    SecretKeyScreen(
        navigation = navigation,
        secretKey = stellarSecretKey,
        title = stringResource(R.string.StellarSecretKey_Title),
        hideScreenText = stringResource(R.string.StellarSecretKey_ShowSecretKey)
    )
}
