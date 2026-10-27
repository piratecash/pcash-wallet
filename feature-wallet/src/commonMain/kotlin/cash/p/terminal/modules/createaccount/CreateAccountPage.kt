package cash.p.terminal.modules.createaccount

import org.jetbrains.compose.resources.getString
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.mnemonic.NonEnglishMnemonicTermsScreen
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.resources.Button_Create
import cash.p.terminal.resources.Res
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui.compose.components.FormsInput
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.CellSingleLineLawrenceSection
import cash.p.terminal.ui_compose.components.HeaderText
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.hdwalletkit.Language
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel
import kotlin.reflect.KClass
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.navigation.rememberResultLauncher
import cash.p.terminal.navigation.slideFromRight
import cash.p.terminal.resources.Button_Advanced
import cash.p.terminal.resources.Hud_Text_Created
import cash.p.terminal.resources.ManageAccount_Name
import cash.p.terminal.resources.ManageAccounts_CreateNewWallet
import cash.p.terminal.resources.ic_arrow_right
import cash.p.terminal.resources.icon_add_to_wallet_24

class CreateAccountPage(val input: Input?) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        if (input?.preselectMonero == true) {
            CreateAccountAdvancedContent(navigation, input)
        } else {
            val viewModel: CreateAdvancedAccountViewModel = koinViewModel()
            CreateAccountIntroScreen(
                viewModel = viewModel,
                openCreateAdvancedScreen = { navigation.slideFromRight(CreateAccountAdvancedPage(input)) },
                onBackClick = navigation::navigateUpSafely,
                onFinish = { navigation.finishCreateAccount(input) },
            )
        }
    }

    data class Input(
        val popOffOnSuccess: KClass<out HSPage>,
        val popOffInclusive: Boolean,
        val preselectMonero: Boolean = false
    )

}

class CreateAccountAdvancedPage(val input: CreateAccountPage.Input?) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        CreateAccountAdvancedContent(navigation, input)
    }
}

class NonEnglishMnemonicTermsPage(val language: Language) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        NonEnglishMnemonicTermsScreen(
            language = language,
            onConfirm = {
                navigation.setResult(this, true)
                navigation.navigateUp()
            },
            onBack = navigation::navigateUp
        )
    }
}

private fun HSNavigation.finishCreateAccount(input: CreateAccountPage.Input?) {
    removeLastUntil(input?.popOffOnSuccess ?: CreateAccountPage::class, input?.popOffInclusive != false)
}

@Composable
private fun CreateAccountAdvancedContent(navigation: HSNavigation, input: CreateAccountPage.Input?) {
    val viewModel: CreateAdvancedAccountViewModel = koinViewModel()
    val passphraseTermsLauncher = navigation.rememberResultLauncher<Boolean> { agreed ->
        if (agreed) viewModel.setPassphraseEnabledState(true)
    }
    val mnemonicTermsLauncher = navigation.rememberResultLauncher<Boolean> { confirmed ->
        if (confirmed) viewModel.createMnemonicAccount()
    }

    CreateAccountAdvancedScreen(
        viewModel = viewModel,
        preselectMonero = input?.preselectMonero == true,
        passphraseTermsAccepted = viewModel.passphraseTermsAgreed,
        onBackClick = navigation::navigateUpSafely,
        onFinish = { navigation.finishCreateAccount(input) },
        onOpenTerms = { passphraseTermsLauncher.slideFromRight(PassphraseTermsPage(screenshotEnabled = true)) },
        onOpenNonEnglishMnemonicTerms = { language ->
            mnemonicTermsLauncher.slideFromRight(NonEnglishMnemonicTermsPage(language))
        }
    )
}

@Composable
private fun CreateAccountIntroScreen(
    viewModel: CreateAdvancedAccountViewModel,
    openCreateAdvancedScreen: () -> Unit,
    onBackClick: () -> Unit,
    onFinish: () -> Unit
) {
    val hudMessenger = rememberHudMessenger()

    LaunchedEffect(viewModel.success) {
        viewModel.success?.let { accountType ->
            hudMessenger.showSuccess(getString(Res.string.Hud_Text_Created), Res.drawable.icon_add_to_wallet_24)
            delay(300)

            onFinish.invoke()
            viewModel.onSuccessMessageShown()
        }
    }

    LaunchedEffect(viewModel.error) {
        viewModel.error?.let { message ->
            hudMessenger.showError(message)
            viewModel.onErrorShown()
        }
    }

    Surface(color = ComposeAppTheme.colors.backgroundBase) {
        Column(Modifier.fillMaxSize()) {
            AppBar(
                title = stringResource(Res.string.ManageAccounts_CreateNewWallet),
                menuItems = listOf(
                    MenuItem(
                        title = TranslatableString.ResString(Res.string.Button_Create),
                        onClick = viewModel::createMnemonicAccount
                    )
                ),
                navigationIcon = {
                    HsBackButton(onClick = onBackClick)
                },
                backgroundColor = Color.Transparent
            )
            Spacer(Modifier.height(12.dp))

            HeaderText(stringResource(Res.string.ManageAccount_Name))
            FormsInput(
                modifier = Modifier.padding(horizontal = 16.dp),
                initial = viewModel.accountName,
                pasteEnabled = false,
                hint = viewModel.defaultAccountName,
                onValueChange = viewModel::onChangeAccountName
            )

            Spacer(Modifier.height(32.dp))

            CellSingleLineLawrenceSection {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable {
                            openCreateAdvancedScreen.invoke()
                        }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    body_leah(text = stringResource(Res.string.Button_Advanced))
                    Spacer(modifier = Modifier.weight(1f))
                    Image(
                        modifier = Modifier.size(20.dp),
                        painter = painterResource(Res.drawable.ic_arrow_right),
                        contentDescription = null,
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
