package cash.p.terminal.modules.restoreaccount.restoremnemonicnonstandard

import cash.p.terminal.modules.restoreaccount.restoremnemonic.ScanQrButton
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.viewmodel.koinViewModel
import cash.p.terminal.resources.Button_Next
import cash.p.terminal.resources.Res
import cash.p.terminal.ui_compose.entities.DataState
import cash.p.terminal.modules.mnemonic.MnemonicLanguageCell
import cash.p.terminal.modules.mnemonic.MnemonicLanguageSelectorDialog
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.rememberQrScannerLauncher
import org.koin.compose.koinInject
import cash.p.terminal.modules.restoreaccount.RestoreViewModel
import cash.p.terminal.modules.restoreaccount.restoremnemonic.SuggestionsBar
import cash.p.terminal.ui.compose.Keyboard
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonSecondaryCircle
import cash.p.terminal.ui_compose.components.ButtonSecondaryDefault
import cash.p.terminal.ui_compose.components.CellSingleLineLawrenceSection
import cash.p.terminal.ui.compose.components.CustomKeyboardWarningDialog
import cash.p.terminal.ui.compose.components.FormsInput
import cash.p.terminal.ui_compose.components.FormsInputPassword
import cash.p.terminal.ui_compose.components.HeaderText
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.HsSwitch
import cash.p.terminal.ui_compose.components.InfoText
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.components.TextImportantWarning
import cash.p.terminal.ui_compose.components.body_disabled
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.caption_lucian
import cash.p.terminal.ui.compose.observeKeyboardState
import cash.p.terminal.ui.compose.rememberInputMethodPicker
import cash.p.terminal.ui.compose.rememberThirdPartyKeyboardCheck
import cash.p.terminal.ui_compose.theme.ColoredTextStyle
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.core.launchAfterClearingFocus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import cash.p.terminal.resources.ManageAccount_Name
import cash.p.terminal.resources.Passphrase
import cash.p.terminal.resources.Restore_NonStandardRestore
import cash.p.terminal.resources.Restore_NonStandard_Description
import cash.p.terminal.resources.Restore_PassphraseDescription
import cash.p.terminal.resources.Restore_PhraseHint
import cash.p.terminal.resources.Restore_RecoveryPhrase
import cash.p.terminal.resources.Send_Button_Paste
import cash.p.terminal.resources.ic_delete_20
import cash.p.terminal.resources.ic_key_phrase_20

@Composable
fun RestorePhraseNonStandard(
    navigation: HSNavigation,
    mainViewModel: RestoreViewModel,
    openSelectCoinsScreen: () -> Unit,
    onBackClick: () -> Unit,
) {
    val viewModel = koinViewModel<RestoreMnemonicNonStandardViewModel>()
    val uiState = viewModel.uiState
    val hudMessenger = rememberHudMessenger()
    val isThirdPartyKeyboard = rememberThirdPartyKeyboardCheck()
    val showInputMethodPicker = rememberInputMethodPicker()
    val focusManager = LocalFocusManager.current
    val scannerTitle = stringResource(Res.string.Restore_RecoveryPhrase)

    var textState by rememberSaveable("", stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    val qrScanner = navigation.rememberQrScannerLauncher(koinInject()) { scannedText ->
        textState = textState.copy(
            text = scannedText,
            selection = TextRange(scannedText.length)
        )
        viewModel.onEnterMnemonicPhrase(scannedText, scannedText.length)
    }
    var showCustomKeyboardDialog by remember { mutableStateOf(false) }
    var isMnemonicPhraseInputFocused by remember { mutableStateOf(false) }
    val keyboardState by observeKeyboardState()

    val borderColor = if (uiState.error != null) {
        ComposeAppTheme.colors.statusError50
    } else {
        ComposeAppTheme.colors.borderDefault
    }

    val coroutineScope = rememberCoroutineScope()
    Column(modifier = Modifier.background(color = ComposeAppTheme.colors.backgroundBase)) {
        AppBar(
            title = stringResource(Res.string.Restore_NonStandardRestore),
            navigationIcon = {
                HsBackButton(onClick = onBackClick)
            },
            menuItems = listOf(
                MenuItem(
                    title = TranslatableString.ResString(Res.string.Button_Next),
                    onClick = viewModel::onProceed
                )
            )
        )
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(12.dp))

                InfoText(text = stringResource(Res.string.Restore_NonStandard_Description))
                Spacer(Modifier.height(12.dp))

                HeaderText(stringResource(Res.string.ManageAccount_Name))
                FormsInput(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    initial = viewModel.accountName,
                    pasteEnabled = false,
                    hint = viewModel.defaultName,
                    onValueChange = viewModel::onEnterName
                )
                Spacer(Modifier.height(32.dp))

                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                        .background(ComposeAppTheme.colors.surfacePrimary),
                ) {

                    val style = SpanStyle(
                        color = ComposeAppTheme.colors.statusError,
                        fontWeight = FontWeight.Normal,
                        fontSize = 16.sp,
                        letterSpacing = 0.sp
                    )

                    BasicTextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged {
                                isMnemonicPhraseInputFocused = it.isFocused
                            }
                            .defaultMinSize(minHeight = 68.dp)
                            .padding(start = 16.dp, end = 16.dp, top = 12.dp),
                        enabled = true,
                        value = textState,
                        onValueChange = {
                            textState = it

                            viewModel.onEnterMnemonicPhrase(it.text, it.selection.max)

                            showCustomKeyboardDialog =
                                !viewModel.isThirdPartyKeyboardAllowed && isThirdPartyKeyboard()
                        },
                        textStyle = ColoredTextStyle(
                            color = ComposeAppTheme.colors.textPrimary,
                            textStyle = ComposeAppTheme.typography.body
                        ),
                        maxLines = 6,
                        cursorBrush = SolidColor(ComposeAppTheme.colors.brandDefault),
                        visualTransformation = {
                            try {
                                val annotatedString = buildAnnotatedString {
                                    append(it.text)

                                    uiState.invalidWordRanges.forEach { range ->
                                        addStyle(style = style, range.first, range.last + 1)
                                    }
                                }
                                TransformedText(annotatedString, OffsetMapping.Identity)
                            } catch (error: Throwable) {
                                error.printStackTrace()
                                TransformedText(it, OffsetMapping.Identity)
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        decorationBox = { innerTextField ->
                            if (textState.text.isEmpty()) {
                                body_disabled(
                                    stringResource(Res.string.Restore_PhraseHint),
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            innerTextField()
                        },
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        if (textState.text.isNotEmpty()) {
                            ButtonSecondaryCircle(
                                modifier = Modifier.padding(end = 16.dp),
                                icon = Res.drawable.ic_delete_20,
                                onClick = {
                                    textState = textState.copy(text = "", selection = TextRange(0))
                                    viewModel.onEnterMnemonicPhrase("", "".length)
                                }
                            )
                        } else {
                            ScanQrButton {
                                coroutineScope.launchAfterClearingFocus(focusManager) {
                                    qrScanner.open(scannerTitle)
                                }
                            }

                            val clipboardManager = LocalClipboardManager.current
                            ButtonSecondaryDefault(
                                modifier = Modifier.padding(end = 16.dp),
                                title = stringResource(Res.string.Send_Button_Paste),
                                onClick = {
                                    clipboardManager.getText()?.text?.let { textInClipboard ->
                                        textState = textState.copy(
                                            text = textInClipboard,
                                            selection = TextRange(textInClipboard.length)
                                        )
                                        viewModel.onEnterMnemonicPhrase(
                                            textInClipboard,
                                            textInClipboard.length
                                        )
                                    }
                                },
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                }

                Spacer(Modifier.height(8.dp))

                uiState.error?.let { errorText ->
                    caption_lucian(
                        modifier = Modifier.padding(horizontal = 32.dp),
                        text = errorText
                    )
                }

                Spacer(Modifier.height(24.dp))

                BottomSection(viewModel, uiState, coroutineScope)

                Spacer(Modifier.height(44.dp))
            }

            if (isMnemonicPhraseInputFocused && keyboardState == Keyboard.Opened) {
                SuggestionsBar(
                    modifier = Modifier.imePadding().align(Alignment.BottomCenter),
                    wordSuggestions = uiState.wordSuggestions
                ) { wordItem, suggestion ->
                    hudMessenger.vibrate()

                    val cursorIndex = wordItem.range.first + suggestion.length + 1
                    var text = textState.text.replaceRange(wordItem.range, suggestion)

                    if (text.length < cursorIndex) {
                        text = "$text "
                    }

                    textState = TextFieldValue(
                        text = text,
                        selection = TextRange(cursorIndex)
                    )

                    viewModel.onEnterMnemonicPhrase(text, cursorIndex)
                }
            }
        }
    }

    uiState.accountType?.let { accountType ->
        mainViewModel.setAccountData(accountType, viewModel.accountName, true, false)
        openSelectCoinsScreen.invoke()
        viewModel.onSelectCoinsShown()
    }

    if (showCustomKeyboardDialog) {
        CustomKeyboardWarningDialog(
            onSelect = {
                showInputMethodPicker()
                showCustomKeyboardDialog = false
            },
            onSkip = {
                viewModel.onAllowThirdPartyKeyboard()
                showCustomKeyboardDialog = false
            },
            onCancel = {
                showCustomKeyboardDialog = false
            }
        )
    }

}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun BottomSection(
    viewModel: RestoreMnemonicNonStandardViewModel,
    uiState: RestoreMnemonicNonStandardUiState,
    coroutineScope: CoroutineScope,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var showLanguageSelectorDialog by remember { mutableStateOf(false) }

    if (showLanguageSelectorDialog) {
        MnemonicLanguageSelectorDialog(
            languages = viewModel.mnemonicLanguages,
            selectedLanguage = uiState.language,
            onDismissRequest = {
                coroutineScope.launch {
                    showLanguageSelectorDialog = false
                    delay(300)
                    keyboardController?.show()
                }
            },
            onSelectLanguage = viewModel::setMnemonicLanguage
        )
    }

    CellSingleLineLawrenceSection(
        listOf({
            MnemonicLanguageCell(
                language = uiState.language,
                showLanguageSelectorDialog = {
                    showLanguageSelectorDialog = true
                },
                enabled = true
            )
        },
            {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_key_phrase_20),
                        tint = ComposeAppTheme.colors.iconSecondary,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(16.dp))
                    body_leah(
                        text = stringResource(Res.string.Passphrase),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    )
                    HsSwitch(
                        checked = uiState.passphraseEnabled,
                        onCheckedChange = viewModel::onTogglePassphrase
                    )
                }
            }
        )
    )

    if (uiState.passphraseEnabled) {
        Spacer(modifier = Modifier.height(24.dp))
        FormsInputPassword(
            modifier = Modifier.padding(horizontal = 16.dp),
            hint = stringResource(Res.string.Passphrase),
            state = uiState.passphraseError?.let { DataState.Error(Exception(it)) },
            onValueChange = viewModel::onEnterPassphrase,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        Spacer(modifier = Modifier.height(16.dp))
        TextImportantWarning(
            modifier = Modifier.padding(horizontal = 16.dp),
            text = stringResource(Res.string.Restore_PassphraseDescription)
        )
    }

}
