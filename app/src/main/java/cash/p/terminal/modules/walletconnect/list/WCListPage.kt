package cash.p.terminal.modules.walletconnect.list

import android.os.Parcelable
import androidx.compose.runtime.Composable
import cash.p.terminal.modules.walletconnect.list.ui.WCSessionsScreen
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import kotlinx.parcelize.Parcelize

class WCListPage(val input: Input?) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        WCSessionsScreen(
            navigation,
            input?.deepLinkUri
        )
    }

    @Parcelize
    data class Input(val deepLinkUri: String) : Parcelable
}
