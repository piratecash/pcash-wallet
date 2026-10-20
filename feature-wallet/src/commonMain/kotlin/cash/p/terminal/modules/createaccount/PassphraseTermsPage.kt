package cash.p.terminal.modules.createaccount

import androidx.compose.runtime.Composable
import cash.p.terminal.modules.createaccount.passphraseterms.PassphraseTermsScreen
import cash.p.terminal.modules.createaccount.passphraseterms.PassphraseTermsViewModel
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import org.jetbrains.compose.resources.stringArrayResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.passphrase_terms_checkboxes

class PassphraseTermsPage(screenshotEnabled: Boolean) : HSPage(screenshotEnabled = screenshotEnabled) {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val termTitles = stringArrayResource(Res.array.passphrase_terms_checkboxes).toTypedArray()
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
