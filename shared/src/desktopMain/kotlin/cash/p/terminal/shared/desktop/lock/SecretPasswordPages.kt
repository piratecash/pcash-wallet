package cash.p.terminal.shared.desktop.lock

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.pin.LevelOutcome
import cash.p.terminal.modules.pin.SetDuressPinIntroPage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpFrom
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.resources.Button_Create
import cash.p.terminal.resources.Button_Save
import cash.p.terminal.resources.Hud_Text_Created
import cash.p.terminal.resources.Password
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.desktop_change_password_in_use
import cash.p.terminal.resources.desktop_duress_password_edit_description
import cash.p.terminal.resources.desktop_duress_password_set_description
import cash.p.terminal.resources.desktop_duress_password_title
import cash.p.terminal.resources.desktop_hidden_wallet_password_description
import cash.p.terminal.resources.desktop_hidden_wallet_password_title
import cash.p.terminal.resources.desktop_levels_limit_reached
import cash.p.terminal.resources.desktop_password_mismatch
import cash.p.terminal.resources.desktop_password_repeat_hint
import cash.p.terminal.resources.desktop_password_save_failed
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.LocalHudMessenger
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.subhead2_lucian
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** [accountIds] are the wallets the duress level will show; empty when there was nothing to choose. */
class SetDuressPasswordPage(private val accountIds: List<String>) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel: DuressPasswordViewModel = koinViewModel { parametersOf(accountIds) }
        val hudMessenger = LocalHudMessenger.current
        val createdMessage = stringResource(Res.string.Hud_Text_Created)
        LaunchedEffect(viewModel.uiState.finished) {
            if (viewModel.uiState.finished) {
                hudMessenger.showSuccess(createdMessage)
                navigation.removeLastUntil(SetDuressPinIntroPage::class, inclusive = true)
            }
        }
        SecretPasswordContent(
            texts = SecretPasswordTexts(
                Res.string.desktop_duress_password_title,
                Res.string.desktop_duress_password_set_description,
                Res.string.Button_Create,
            ),
            uiState = viewModel.uiState,
            onPasswordChange = viewModel::onPasswordChange,
            onConfirmationChange = viewModel::onConfirmationChange,
            onSubmitClick = viewModel::onSubmitClick,
            onBack = navigation::navigateUpSafely,
        )
    }
}

class EditDuressPasswordPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel: DuressPasswordViewModel = koinViewModel { parametersOf(emptyList<String>()) }
        LaunchedEffect(viewModel.uiState.finished) {
            if (viewModel.uiState.finished) navigation.navigateUpFrom(this@EditDuressPasswordPage)
        }
        SecretPasswordContent(
            texts = SecretPasswordTexts(
                Res.string.desktop_duress_password_title,
                Res.string.desktop_duress_password_edit_description,
                Res.string.Button_Save,
            ),
            uiState = viewModel.uiState,
            onPasswordChange = viewModel::onPasswordChange,
            onConfirmationChange = viewModel::onConfirmationChange,
            onSubmitClick = viewModel::onSubmitClick,
            onBack = navigation::navigateUpSafely,
        )
    }
}

/** On success the session is replaced by the hidden wallet's, and with it this page. */
class HiddenWalletPasswordPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel: HiddenWalletPasswordViewModel = koinViewModel()
        SecretPasswordContent(
            texts = SecretPasswordTexts(
                Res.string.desktop_hidden_wallet_password_title,
                Res.string.desktop_hidden_wallet_password_description,
                Res.string.Button_Create,
            ),
            uiState = viewModel.uiState,
            onPasswordChange = viewModel::onPasswordChange,
            onConfirmationChange = viewModel::onConfirmationChange,
            onSubmitClick = viewModel::onSubmitClick,
            onBack = navigation::navigateUpSafely,
        )
    }
}

internal class SecretPasswordTexts(
    val title: StringResource,
    val description: StringResource,
    val buttonTitle: StringResource,
)

@Composable
internal fun SecretPasswordContent(
    texts: SecretPasswordTexts,
    uiState: SecretPasswordUiState,
    onPasswordChange: (String) -> Unit,
    onConfirmationChange: (String) -> Unit,
    onSubmitClick: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = {
            AppBar(
                title = stringResource(texts.title),
                navigationIcon = { HsBackButton(onClick = onBack) },
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .centeredForm(rememberScrollState()),
        ) {
            VSpacer(24.dp)
            subhead2_grey(text = stringResource(texts.description))
            VSpacer(16.dp)
            DesktopPasswordField(
                value = uiState.password,
                hint = stringResource(Res.string.Password),
                onValueChange = onPasswordChange,
                onSubmit = onSubmitClick,
                enabled = !uiState.inProgress,
                error = stringResource(Res.string.desktop_change_password_in_use)
                    .takeIf { uiState.outcome == LevelOutcome.PASSWORD_IN_USE },
            )
            VSpacer(16.dp)
            DesktopPasswordField(
                value = uiState.confirmation,
                hint = stringResource(Res.string.desktop_password_repeat_hint),
                onValueChange = onConfirmationChange,
                onSubmit = onSubmitClick,
                enabled = !uiState.inProgress,
                error = stringResource(Res.string.desktop_password_mismatch).takeIf { uiState.mismatch },
            )
            VSpacer(16.dp)
            PasswordRequirements(uiState.unmetRequirements, uiState.invalidCharacters)
            VSpacer(24.dp)
            ButtonPrimaryYellow(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(texts.buttonTitle),
                onClick = onSubmitClick,
                enabled = uiState.canSubmit,
                loadingIndicator = uiState.inProgress,
            )
            uiState.outcome?.messageBelowForm()?.let {
                VSpacer(12.dp)
                subhead2_lucian(text = stringResource(it))
            }
            VSpacer(24.dp)
        }
    }
}

private fun LevelOutcome.messageBelowForm(): StringResource? = when (this) {
    LevelOutcome.LIMIT_REACHED -> Res.string.desktop_levels_limit_reached
    LevelOutcome.FAILED -> Res.string.desktop_password_save_failed
    LevelOutcome.SUCCESS, LevelOutcome.PASSWORD_IN_USE -> null
}

@Preview
@Composable
private fun SecretPasswordContentPreview() {
    ComposeAppTheme {
        SecretPasswordContent(
            texts = SecretPasswordTexts(
                Res.string.desktop_duress_password_title,
                Res.string.desktop_duress_password_set_description,
                Res.string.Button_Create,
            ),
            uiState = SecretPasswordUiState(password = "secret", outcome = LevelOutcome.PASSWORD_IN_USE),
            onPasswordChange = {},
            onConfirmationChange = {},
            onSubmitClick = {},
            onBack = {},
        )
    }
}
