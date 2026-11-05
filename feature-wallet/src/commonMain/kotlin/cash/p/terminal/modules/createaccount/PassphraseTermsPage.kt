package cash.p.terminal.modules.createaccount

import androidx.compose.runtime.Composable
import cash.p.terminal.modules.createaccount.passphraseterms.PassphraseTermsScreen
import cash.p.terminal.modules.createaccount.passphraseterms.PassphraseTermsViewModel
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.passphrase_terms_checkboxes
import cash.p.terminal.resources.passphrase_terms_restore_shamir
import cash.p.terminal.resources.passphrase_terms_warning
import cash.p.terminal.resources.passphrase_terms_warning_shamir

class PassphraseTermsPage(
    screenshotEnabled: Boolean,
    private val isShamir: Boolean = false,
) : HSPage(screenshotEnabled = screenshotEnabled) {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val termTitles = stringArrayResource(Res.array.passphrase_terms_checkboxes).toTypedArray()
        if (isShamir) termTitles[RESTORE_TERM_INDEX] = stringResource(Res.string.passphrase_terms_restore_shamir)
        val viewModel = koinViewModel<PassphraseTermsViewModel> { parametersOf(termTitles) }
        val warning = if (isShamir) Res.string.passphrase_terms_warning_shamir else Res.string.passphrase_terms_warning

        PassphraseTermsScreen(
            uiState = viewModel.uiState,
            warning = stringResource(warning),
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

/** Position of the "restored only in apps supporting the standard" item in `passphrase_terms_checkboxes`. */
private const val RESTORE_TERM_INDEX = 3
