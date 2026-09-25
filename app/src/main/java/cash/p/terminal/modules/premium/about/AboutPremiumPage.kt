package cash.p.terminal.modules.premium.about

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import cash.p.terminal.modules.markdown.openMarkdownOrWeblink
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import kotlinx.parcelize.Parcelize
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
                navigation.openMarkdownOrWeblink(url)
            },
            onTryForFreeClick = viewModel::activateDemoPremium
        )
    }

    @Parcelize
    class CloseOnPremiumInput : Parcelable

    @Parcelize
    class Result : Parcelable
}
