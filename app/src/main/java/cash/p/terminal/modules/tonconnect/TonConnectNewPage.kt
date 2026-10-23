package cash.p.terminal.modules.tonconnect

import android.os.Parcelable
import androidx.compose.runtime.Composable
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpFrom
import cash.p.terminal.navigation.navigateUpSafely
import kotlinx.parcelize.Parcelize

class TonConnectNewPage(val uri: String) : HSPage() {
    @Composable
    override fun GetContent(navigation: HSNavigation) {
        TonConnectNewScreen(
            navigation = navigation,
            uri = uri,
            onResult = { approved ->
                navigation.setResult(this, Result(approved))
                // Approval closes from state, right after the PIN page took the back-navigation cooldown.
                if (approved) navigation.navigateUpFrom(this) else navigation.navigateUpSafely()
            },
        )
    }

    @Parcelize
    data class Result(val approved: Boolean) : Parcelable
}
