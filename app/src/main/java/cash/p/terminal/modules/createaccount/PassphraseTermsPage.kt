package cash.p.terminal.modules.createaccount

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import cash.p.terminal.R
import cash.p.terminal.modules.createaccount.passphraseterms.PassphraseTermsScreen
import cash.p.terminal.modules.createaccount.passphraseterms.PassphraseTermsViewModel
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import cash.p.terminal.navigation.navigateUpSafely

class PassphraseTermsPage(screenshotEnabled: Boolean) : HSPage(screenshotEnabled = screenshotEnabled) {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val context = LocalContext.current
        val termTitles = context.resources.getStringArray(R.array.passphrase_terms_checkboxes)
        val viewModel = koinViewModel<PassphraseTermsViewModel> { parametersOf(termTitles) }

        PassphraseTermsScreen(
            uiState = viewModel.uiState,
            onCheckboxToggle = viewModel::toggleCheckbox,
            onAgreeClick = {
                viewModel.agree()
                navigation.setResult(this, true)
                navigation.navigateUpSafely()
            },
            onBackClick = navigation::navigateUpSafely
        )
    }
}
