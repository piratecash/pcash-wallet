package cash.p.terminal.modules.createaccount

import org.jetbrains.compose.resources.getString
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.coin.overview.ui.Loading
import cash.p.terminal.modules.mnemonic.MnemonicLanguageCell
import cash.p.terminal.modules.mnemonic.MnemonicLanguageSelectorDialog
import cash.p.terminal.resources.Button_Create
import cash.p.terminal.resources.Res
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui.compose.components.FormsInput
import cash.p.terminal.ui_compose.components.AppSelectorDialog
import cash.p.terminal.ui_compose.components.AppSelectorItem
import cash.p.terminal.ui_compose.blockClicksBehind
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.B2
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.D1
import cash.p.terminal.ui_compose.components.FormsInputPassword
import cash.p.terminal.ui_compose.components.HeaderText
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.HsSwitch
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.subhead1_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.data.MnemonicKind
import io.horizontalsystems.hdwalletkit.Language
import kotlinx.coroutines.delay
import cash.p.terminal.resources.ConfirmPassphrase
import cash.p.terminal.resources.CreateWallet_Advanced_Title
import cash.p.terminal.resources.CreateWallet_Mnemonic
import cash.p.terminal.resources.CreateWallet_PassphraseDescription
import cash.p.terminal.resources.Hud_Text_Created
import cash.p.terminal.resources.ManageAccount_Name
import cash.p.terminal.resources.Passphrase
import cash.p.terminal.resources.ic_arrow_right
import cash.p.terminal.resources.ic_down_arrow_20
import cash.p.terminal.resources.ic_key_20
import cash.p.terminal.resources.ic_key_phrase_20
import cash.p.terminal.resources.icon_add_to_wallet_24
import cash.p.terminal.resources.passphrase_terms_title

@Composable
fun CreateAccountAdvancedScreen(
    viewModel: CreateAdvancedAccountViewModel,
    preselectMonero: Boolean,
    passphraseTermsAccepted: Boolean,
    onBackClick: () -> Unit,
    onFinish: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenNonEnglishMnemonicTerms: (Language) -> Unit
) {
    LaunchedEffect(Unit) {
        if (preselectMonero) {
            viewModel.setMnemonicKind(MnemonicKind.Mnemonic25)
        }
    }
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

    var showMnemonicSizeSelectorDialog by remember { mutableStateOf(false) }
    var showLanguageSelectorDialog by remember { mutableStateOf(false) }

    Surface(color = ComposeAppTheme.colors.backgroundBase) {
        if (showMnemonicSizeSelectorDialog) {
            AppSelectorDialog(
                title = stringResource(Res.string.CreateWallet_Mnemonic),
                items = viewModel.mnemonicKinds.map {
                    AppSelectorItem(it.titleLong, it == viewModel.selectedKind, it)
                },
                onDismiss = {
                    showMnemonicSizeSelectorDialog = false
                },
                onSelect = {
                    viewModel.setMnemonicKind(it)
                }
            )
        }
        if (showLanguageSelectorDialog) {
            MnemonicLanguageSelectorDialog(
                languages = viewModel.mnemonicLanguages,
                selectedLanguage = viewModel.selectedLanguage,
                onDismissRequest = {
                    showLanguageSelectorDialog = false
                },
                onSelectLanguage = viewModel::setMnemonicLanguage
            )
        }
        Column {
            AppBar(
                title = stringResource(Res.string.CreateWallet_Advanced_Title),
                navigationIcon = {
                    HsBackButton(onClick = onBackClick)
                },
                menuItems = listOf(
                    MenuItem(
                        title = TranslatableString.ResString(Res.string.Button_Create),
                        onClick = {
                            if (viewModel.shouldConfirmNonEnglishMnemonic()) {
                                onOpenNonEnglishMnemonicTerms(viewModel.selectedLanguage)
                            } else {
                                viewModel.createMnemonicAccount()
                            }
                        },
                    )
                )
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
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
                    CellUniversalLawrenceSection(
                        listOf(
                            {
                                MnemonicNumberCell(
                                    kind = viewModel.selectedKind,
                                    showMnemonicSizeSelectorDialog = {
                                        showMnemonicSizeSelectorDialog = true
                                    }
                                )
                            },
                            {
                                MnemonicLanguageCell(
                                    language = viewModel.displayedLanguage,
                                    showLanguageSelectorDialog = {
                                        showLanguageSelectorDialog = true
                                    },
                                    enabled = viewModel.languageSelectionEnabled
                                )
                            }
                        )
                    )

                    if (viewModel.showPassphraseBlock) {
                        Spacer(Modifier.height(32.dp))
                        if (passphraseTermsAccepted) {
                            CellUniversalLawrenceSection(
                                listOf {
                                    RowUniversal(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        onClick = onOpenTerms
                                    ) {
                                        body_leah(
                                            text = stringResource(Res.string.passphrase_terms_title),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Image(
                                            modifier = Modifier.size(20.dp),
                                            painter = painterResource(Res.drawable.ic_arrow_right),
                                            contentDescription = null,
                                        )
                                    }
                                }
                            )
                            Spacer(Modifier.height(24.dp))
                        }
                        CellUniversalLawrenceSection(listOf {
                            PassphraseCell(
                                enabled = viewModel.passphraseEnabled,
                                onCheckedChange = {
                                    if (passphraseTermsAccepted) {
                                        viewModel.setPassphraseEnabledState(it)
                                    } else {
                                        onOpenTerms()
                                    }
                                }
                            )
                        })
                        if (viewModel.passphraseEnabled) {
                            Spacer(Modifier.height(24.dp))
                            FormsInputPassword(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                hint = stringResource(Res.string.Passphrase),
                                state = viewModel.passphraseState,
                                onValueChange = viewModel::onChangePassphrase,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            )
                            Spacer(Modifier.height(16.dp))
                            FormsInputPassword(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                hint = stringResource(Res.string.ConfirmPassphrase),
                                state = viewModel.passphraseConfirmState,
                                onValueChange = viewModel::onChangePassphraseConfirmation,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            )
                            Spacer(Modifier.height(12.dp))
                            D1(
                                modifier = Modifier.padding(horizontal = 24.dp),
                                text = stringResource(Res.string.CreateWallet_PassphraseDescription)
                            )
                        }
                    }
                    Spacer(Modifier.height(32.dp))
                }
                if (viewModel.loading) {
                    Loading(
                        modifier = Modifier
                            .background(color = ComposeAppTheme.colors.backgroundBase.copy(0.5f))
                            .blockClicksBehind()
                    )
                }
            }
        }
    }
}

@Composable
private fun MnemonicNumberCell(
    kind: MnemonicKind,
    showMnemonicSizeSelectorDialog: () -> Unit
) {
    RowUniversal(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalPadding = 0.dp,
        onClick = { showMnemonicSizeSelectorDialog() }
    ) {
        Icon(
            modifier = Modifier.padding(vertical = 12.dp),
            painter = painterResource(Res.drawable.ic_key_20),
            contentDescription = null,
            tint = ComposeAppTheme.colors.iconSecondary
        )
        B2(
            text = stringResource(Res.string.CreateWallet_Mnemonic),
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.weight(1f))
        subhead1_grey(
            text = kind.title,
        )
        Icon(
            modifier = Modifier.padding(start = 4.dp),
            painter = painterResource(Res.drawable.ic_down_arrow_20),
            contentDescription = null,
            tint = ComposeAppTheme.colors.iconSecondary
        )
    }
}

@Composable
fun PassphraseCell(enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    RowUniversal(
        modifier = Modifier.padding(horizontal = 16.dp),
        onClick = { onCheckedChange(!enabled) },
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_key_phrase_20),
            contentDescription = null,
            tint = ComposeAppTheme.colors.iconSecondary
        )
        body_leah(
            text = stringResource(Res.string.Passphrase),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp, end = 8.dp)
        )
        HsSwitch(
            checked = enabled,
            onCheckedChange = onCheckedChange
        )
    }
}
