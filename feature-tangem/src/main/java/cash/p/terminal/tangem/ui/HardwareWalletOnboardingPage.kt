package cash.p.terminal.tangem.ui

import android.os.Parcelable
import androidx.compose.runtime.Composable
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.tangem.ui.onboarding.OnboardingScreen
import kotlinx.parcelize.Parcelize
import org.koin.compose.viewmodel.koinViewModel

class HardwareWalletOnboardingPage(val input: Input) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel = koinViewModel<HardwareWalletOnboardingViewModel>()
        viewModel.accountName = input.name
        OnboardingScreen(
            viewModel = viewModel,
            navigation = navigation,
            onSuccess = {
                navigation.setResult(this, Result(true))
                navigation.navigateUp()
            }
        )
    }

    @Parcelize
    data class Result(val success: Boolean) : Parcelable

    @Parcelize
    data class Input(val name: String) : Parcelable
}
