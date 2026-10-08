package cash.p.terminal.modules.tonconnect

import android.os.Parcelable
import androidx.compose.runtime.Composable
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import kotlinx.parcelize.Parcelize
import org.koin.compose.koinInject

class TonConnectMainPage(val input: Input?) : HSPage() {
    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel: TonConnectListViewModel = koinInject()

        TonConnectMainScreen(viewModel, navigation, input?.deepLinkUri)
    }

    @Parcelize
    data class Input(val deepLinkUri: String) : Parcelable
}
