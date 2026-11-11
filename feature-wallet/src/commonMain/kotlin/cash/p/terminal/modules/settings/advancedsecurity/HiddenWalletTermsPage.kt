package cash.p.terminal.modules.settings.advancedsecurity

import androidx.compose.runtime.Composable
import cash.p.terminal.modules.pin.DuressSecretPages
import cash.p.terminal.modules.settings.advancedsecurity.terms.HiddenWalletTermsScreen
import cash.p.terminal.modules.settings.advancedsecurity.terms.HiddenWalletTermsViewModel
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import org.jetbrains.compose.resources.stringArrayResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class HiddenWalletTermsPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val pages = koinInject<DuressSecretPages>()
        val termTitles = stringArrayResource(pages.wording.termsCheckboxes).toTypedArray()
        val viewModel: HiddenWalletTermsViewModel = koinViewModel { parametersOf(termTitles) }

        HiddenWalletTermsScreen(
            uiState = viewModel.uiState,
            warning = pages.wording.termsWarning,
            onCheckboxToggle = viewModel::toggleCheckbox,
            onAgreeClick = pages.rememberOpenHiddenWalletSecret(navigation),
            onNavigateBack = navigation::navigateUpSafely
        )
    }
}
