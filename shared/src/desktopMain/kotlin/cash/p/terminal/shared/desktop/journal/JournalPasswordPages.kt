package cash.p.terminal.shared.desktop.journal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import cash.p.terminal.feature.logging.settings.LoginJournalSecrets
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpFrom
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.navigation.rememberResultLauncher
import cash.p.terminal.navigation.slideFromBottom
import cash.p.terminal.resources.Button_Create
import cash.p.terminal.resources.Hud_Text_Created
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.desktop_delete_contacts_password_set_description
import cash.p.terminal.resources.desktop_delete_contacts_password_title
import cash.p.terminal.resources.desktop_journal_password_access_description
import cash.p.terminal.resources.desktop_journal_password_set_description
import cash.p.terminal.resources.desktop_journal_password_title
import cash.p.terminal.shared.desktop.lock.ConfirmPasswordContent
import cash.p.terminal.shared.desktop.lock.ConfirmPasswordPage
import cash.p.terminal.shared.desktop.lock.SecretPasswordContent
import cash.p.terminal.shared.desktop.lock.SecretPasswordTexts
import cash.p.terminal.ui_compose.components.LocalHudMessenger
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Runs [action] once the journal is opened: with its own password when it has one, otherwise with the wallet's. */
@Composable
fun HSNavigation.rememberJournalAccessAction(action: () -> Unit): () -> Unit =
    rememberJournalAccessAction(Res.string.desktop_journal_password_access_description, action)

@Composable
fun HSNavigation.rememberJournalAccessAction(prompt: StringResource, action: () -> Unit): () -> Unit {
    val secrets: LoginJournalSecrets = koinInject()
    val launcher = rememberResultLauncher<ConfirmPasswordPage.Result> { result -> if (result.success) action() }
    return {
        launcher.slideFromBottom(
            if (secrets.isJournalSecretSet()) {
                ConfirmJournalSecretPage(JournalSecretKind.JOURNAL_PASSWORD, prompt)
            } else {
                ConfirmPasswordPage()
            }
        )
    }
}

/** Reports [ConfirmPasswordPage.Result] like the wallet's own confirmation, so one launcher serves both. */
class ConfirmJournalSecretPage(
    private val kind: JournalSecretKind,
    private val prompt: StringResource,
) : HSPage(screenshotEnabled = false) {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel: JournalSecretCheckViewModel = koinViewModel { parametersOf(kind) }
        LaunchedEffect(viewModel.confirmed) {
            if (viewModel.confirmed && navigation.navigateUpFrom(this@ConfirmJournalSecretPage)) {
                navigation.setResult(this@ConfirmJournalSecretPage, ConfirmPasswordPage.Result(success = true))
            }
        }
        ConfirmPasswordContent(
            uiState = viewModel.uiState,
            onPasswordChange = viewModel::onPasswordChange,
            onConfirmClick = viewModel::onSubmitClick,
            onBack = navigation::navigateUpSafely,
            description = prompt,
        )
    }
}

class SetJournalSecretPage(private val kind: JournalSecretKind) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel: JournalSecretPasswordViewModel = koinViewModel { parametersOf(kind) }
        val hudMessenger = LocalHudMessenger.current
        val createdMessage = stringResource(Res.string.Hud_Text_Created)
        LaunchedEffect(viewModel.uiState.finished) {
            if (!viewModel.uiState.finished) return@LaunchedEffect
            hudMessenger.showSuccess(createdMessage)
            when (kind) {
                JournalSecretKind.JOURNAL_PASSWORD -> navigation.navigateUpFrom(this@SetJournalSecretPage)
                JournalSecretKind.DELETE_CONTACTS ->
                    navigation.removeLastUntil(DesktopDeleteContactsTermsPage::class, inclusive = true)
            }
        }
        SecretPasswordContent(
            texts = kind.texts(),
            uiState = viewModel.uiState,
            onPasswordChange = viewModel::onPasswordChange,
            onConfirmationChange = viewModel::onConfirmationChange,
            onSubmitClick = viewModel::onSubmitClick,
            onBack = navigation::navigateUpSafely,
        )
    }

    private fun JournalSecretKind.texts() = when (this) {
        JournalSecretKind.JOURNAL_PASSWORD -> SecretPasswordTexts(
            Res.string.desktop_journal_password_title,
            Res.string.desktop_journal_password_set_description,
            Res.string.Button_Create,
        )
        JournalSecretKind.DELETE_CONTACTS -> SecretPasswordTexts(
            Res.string.desktop_delete_contacts_password_title,
            Res.string.desktop_delete_contacts_password_set_description,
            Res.string.Button_Create,
        )
    }
}
