package cash.p.terminal.modules.tonconnect

import android.os.Parcelable
import androidx.compose.runtime.Composable
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import com.tonapps.wallet.data.tonconnect.entities.DAppRequestEntity
import kotlinx.parcelize.Parcelize

class TonConnectNewPage(val input: DAppRequestEntity) : HSPage() {
    @Composable
    override fun GetContent(navigation: HSNavigation) {
        TonConnectNewScreen(
            navigation = navigation,
            requestEntity = input,
            onResult = { approved ->
                navigation.setResult(this, Result(approved))
                navigation.navigateUpSafely()
            },
        )
    }

    @Parcelize
    data class Result(val approved: Boolean) : Parcelable
}
