package cash.p.terminal.modules.premium.about

import io.horizontalsystems.core.common.CommonParcelable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.feature.wallet.WalletPlatformPages
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import io.horizontalsystems.core.common.CommonParcelize
import org.koin.compose.viewmodel.koinViewModel

class AboutPremiumPage(val input: CloseOnPremiumInput?) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel: AboutPremiumViewModel = koinViewModel()

        LaunchedEffect(viewModel.uiState.hasPremium) {
            if (viewModel.uiState.hasPremium && input != null) {
                navigation.setResult(this@AboutPremiumPage, Result())
                navigation.navigateUp()
            }
        }
        AboutPremiumScreen(
            uiState = viewModel.uiState,
            uiEvents = viewModel.uiEvents,
            onRetryClick = viewModel::retry,
            onCloseClick = { navigation.navigateUpSafely() },
            onUrlClick = { url ->
                getKoinInstance<WalletPlatformPages>().openMarkdownOrWeblink(navigation, url)
            },
            onTryForFreeClick = viewModel::activateDemoPremium
        )
    }

    @CommonParcelize
    class CloseOnPremiumInput : CommonParcelable

    @CommonParcelize
    class Result : CommonParcelable
}
