package cash.p.terminal.modules.tonconnect

import androidx.compose.runtime.Composable
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import io.horizontalsystems.core.common.CommonParcelable
import io.horizontalsystems.core.common.CommonParcelize
import org.koin.compose.koinInject

class TonConnectMainPage(val input: Input?) : HSPage() {
    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel: TonConnectListViewModel = koinInject()

        TonConnectMainScreen(viewModel, navigation, input?.deepLinkUri)
    }

    @CommonParcelize
    data class Input(val deepLinkUri: String) : CommonParcelable
}
