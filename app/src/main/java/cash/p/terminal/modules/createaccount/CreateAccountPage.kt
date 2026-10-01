package cash.p.terminal.modules.createaccount

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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.modules.mnemonic.NonEnglishMnemonicTermsScreen
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui.compose.components.FormsInput
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.CellSingleLineLawrenceSection
import cash.p.terminal.ui_compose.components.HeaderText
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.HudHelper
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.hdwalletkit.Language
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel
import kotlin.reflect.KClass
import cash.p.terminal.navigation.navigateUpSafely

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

    CreateAccountAdvancedScreen(
        viewModel = viewModel,
        preselectMonero = input?.preselectMonero == true,
        passphraseTermsAccepted = viewModel.passphraseTermsAgreed,
        onBackClick = navigation::navigateUpSafely,
        onFinish = { navigation.finishCreateAccount(input) },
        onOpenTerms = {
            navigation.slideFromRightForResult<Boolean>(PassphraseTermsPage(screenshotEnabled = true)) { agreed ->
                if (agreed) viewModel.setPassphraseEnabledState(true)
            }
        },
        onOpenNonEnglishMnemonicTerms = { language ->
            navigation.slideFromRightForResult<Boolean>(NonEnglishMnemonicTermsPage(language)) { confirmed ->
                if (confirmed) viewModel.createMnemonicAccount()
            }
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
    val view = LocalView.current

    LaunchedEffect(viewModel.success) {
        viewModel.success?.let { accountType ->
            HudHelper.showSuccessMessage(
                contenView = view,
                resId = R.string.Hud_Text_Created,
                icon = R.drawable.icon_add_to_wallet_24,
                iconTint = R.color.white
            )
            delay(300)

            onFinish.invoke()
            viewModel.onSuccessMessageShown()
        }
    }

    LaunchedEffect(viewModel.error) {
        viewModel.error?.let { message ->
            HudHelper.showErrorMessage(contentView = view, text = message)
            viewModel.onErrorShown()
        }
    }

    Surface(color = ComposeAppTheme.colors.tyler) {
        Column(Modifier.fillMaxSize()) {
            AppBar(
                title = stringResource(R.string.ManageAccounts_CreateNewWallet),
                menuItems = listOf(
                    MenuItem(
                        title = TranslatableString.ResString(R.string.Button_Create),
                        onClick = viewModel::createMnemonicAccount
                    )
                ),
                navigationIcon = {
                    HsBackButton(onClick = onBackClick)
                },
                backgroundColor = Color.Transparent
            )
            Spacer(Modifier.height(12.dp))

            HeaderText(stringResource(id = R.string.ManageAccount_Name))
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
                    body_leah(text = stringResource(R.string.Button_Advanced))
                    Spacer(modifier = Modifier.weight(1f))
                    Image(
                        modifier = Modifier.size(20.dp),
                        painter = painterResource(id = R.drawable.ic_arrow_right),
                        contentDescription = null,
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
