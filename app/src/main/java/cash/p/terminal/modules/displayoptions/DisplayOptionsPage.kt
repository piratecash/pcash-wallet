package cash.p.terminal.modules.displayoptions

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import kotlinx.parcelize.Parcelize
import org.koin.compose.viewmodel.koinViewModel

class DisplayOptionsPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel: DisplayOptionsViewModel = koinViewModel()
        val uiState = viewModel.uiState.collectAsStateWithLifecycle()

        navigation.setResult(this, Result())
        DisplayOptionsScreen(
            navigation = navigation,
            uiState = uiState.value,
            onPricePeriodChanged = viewModel::onPricePeriodChanged,
            onPercentChangeToggled = viewModel::onPercentChangeToggled,
            onPriceChangeToggled = viewModel::onPriceChangeToggled,
            onRoundingAmountMainPageToggled = viewModel::onRoundingAmountMainPageToggled
        )
    }

    @Parcelize
    class Result : Parcelable
}
