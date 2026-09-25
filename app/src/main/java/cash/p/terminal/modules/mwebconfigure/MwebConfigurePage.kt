package cash.p.terminal.modules.mwebconfigure

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import cash.p.terminal.R
import cash.p.terminal.core.title
import cash.p.terminal.modules.enablecoin.restoresettings.TokenConfig
import cash.p.terminal.modules.moneroconfigure.MoneroConfigureRoute
import cash.p.terminal.modules.moneroconfigure.OneShotBackHandler
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.parcelize.Parcelize
import org.koin.compose.viewmodel.koinViewModel

class MwebConfigurePage(val input: Input) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        OneShotBackHandler { close(navigation) }
        val initialConfig = input.initialConfig
        val viewModel: MwebConfigureViewModel = koinViewModel()

        LaunchedEffect(initialConfig) {
            viewModel.setInitialConfig(initialConfig)
        }

        MoneroConfigureRoute(
            title = TokenType.Mweb.title,
            blockchainType = BlockchainType.Litecoin,
            heightHintRes = R.string.restoreheight_hint_block_only,
            onCloseWithResult = {
                viewModel.onClosed()
                closeWithConfig(it, navigation)
            },
            onCloseClick = { close(navigation) },
            onModeSelect = viewModel::onModeSelect,
            onSetBirthdayHeight = viewModel::setBirthdayHeight,
            onDoneClick = viewModel::onDoneClick,
            uiState = viewModel.uiState,
        )
    }

    private fun closeWithConfig(config: TokenConfig, navigation: HSNavigation) {
        navigation.setResult(this, Result(config))
        navigation.navigateUp()
    }

    private fun close(navigation: HSNavigation) {
        navigation.setResult(this, Result(null))
        navigation.navigateUpSafely()
    }

    @Parcelize
    data class Result(val config: TokenConfig?) : Parcelable

    @Parcelize
    data class Input(val initialConfig: TokenConfig?) : Parcelable
}
