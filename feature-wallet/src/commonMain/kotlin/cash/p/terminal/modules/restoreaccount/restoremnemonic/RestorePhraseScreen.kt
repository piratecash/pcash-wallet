package cash.p.terminal.modules.restoreaccount.restoremnemonic

import cash.p.terminal.feature.wallet.qrScannerAvailable
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
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
import androidx.compose.ui.graphics.Color
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
import cash.p.terminal.core.launchAfterClearingFocus
import cash.p.terminal.modules.createaccount.PassphraseCell
import cash.p.terminal.modules.mnemonic.MnemonicLanguageCell
import cash.p.terminal.modules.mnemonic.MnemonicLanguageSelectorDialog
import cash.p.terminal.modules.restoreaccount.RestoreViewModel
import cash.p.terminal.modules.restoreaccount.restoremenu.RestoreByMenu
import cash.p.terminal.modules.restoreaccount.restoremenu.RestoreMenuViewModel
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.rememberQrScannerLauncher
import cash.p.terminal.resources.Button_Next
import cash.p.terminal.resources.Res
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui.compose.Keyboard
import cash.p.terminal.ui.compose.components.BoxTyler44
import cash.p.terminal.ui.compose.components.CustomKeyboardWarningDialog
import cash.p.terminal.ui.compose.components.FormsInput
import cash.p.terminal.ui.compose.components.RestoreHeightInput
import cash.p.terminal.ui.compose.components.SelectDateBottomSheet
import cash.p.terminal.ui.compose.components.restoreGenesisDateMillis
import cash.p.terminal.ui.compose.components.restoreMaxDateMillis
import cash.p.terminal.ui.compose.observeKeyboardState
import cash.p.terminal.ui.compose.rememberInputMethodPicker
import cash.p.terminal.ui.compose.rememberThirdPartyKeyboardCheck
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonSecondary
import cash.p.terminal.ui_compose.components.ButtonSecondaryCircle
import cash.p.terminal.ui_compose.components.ButtonSecondaryDefault
import cash.p.terminal.ui_compose.components.CellSingleLineLawrenceSection
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.FormsInputPassword
import cash.p.terminal.ui_compose.components.HeaderText
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.HsSwitch
import cash.p.terminal.ui_compose.components.InfoText
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.TextImportantWarning
import cash.p.terminal.ui_compose.components.body_disabled
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.subhead2_leah
import cash.p.terminal.ui_compose.components.caption_lucian
import cash.p.terminal.ui_compose.entities.DataState
import cash.p.terminal.ui_compose.theme.ColoredTextStyle
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.AccountType
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.hdwalletkit.Language
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import cash.p.terminal.resources.Button_Advanced
import cash.p.terminal.resources.ManageAccount_Name
import cash.p.terminal.resources.ManageAccounts_ImportWallet
import cash.p.terminal.resources.Passphrase
import cash.p.terminal.resources.Restore_Advanced_Title
import cash.p.terminal.resources.Restore_NonStandardRestore
import cash.p.terminal.resources.Restore_PassphraseDescription
import cash.p.terminal.resources.Restore_PhraseHint
import cash.p.terminal.resources.Restore_RecoveryPhrase
import cash.p.terminal.resources.Send_Button_Paste
import cash.p.terminal.resources.ic_arrow_right
import cash.p.terminal.resources.ic_delete_20
import cash.p.terminal.resources.ic_more_24
import cash.p.terminal.resources.ic_qr_scan_20
import cash.p.terminal.resources.monero_restore_seed
import cash.p.terminal.resources.restoreheight_hint
import cash.p.terminal.resources.restoreheight_title
import cash.p.terminal.resources.select_date_description

@Composable
fun RestorePhrase(
    navigation: HSNavigation,
    restoreMenuViewModel: RestoreMenuViewModel,
    mainViewModel: RestoreViewModel,
    openSelectCoins: () -> Unit,
    openNonStandardRestore: () -> Unit,
    onBackClick: () -> Unit,
    onFinish: () -> Unit,
    advanced: Boolean = false,
    openRestoreAdvanced: (() -> Unit)? = null,
    prefillWords: List<String>? = null,
    prefillPassphrase: String? = null,
    prefillMoneroHeight: Long? = null,
    prefillMnemonicLanguage: Language? = null
) {
    val viewModel = koinViewModel<RestoreMnemonicViewModel>()
    val uiState = viewModel.uiState
    val isThirdPartyKeyboard = rememberThirdPartyKeyboardCheck()
    val showInputMethodPicker = rememberInputMethodPicker()
    val hudMessenger = rememberHudMessenger()
    val focusManager = LocalFocusManager.current
    val scannerTitle = stringResource(Res.string.Restore_RecoveryPhrase)

    // Build initial text from prefill data (words only, passphrase handled separately)
    val initialText = remember(prefillWords) {
        prefillWords?.joinToString(" ") ?: ""
    }

    var textState by rememberSaveable(initialText, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialText, TextRange(initialText.length)))
    }

    // Passphrase text state hoisted for QR scan support (as MutableState for FormsInputPassword)
    val passphraseTextState = rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(prefillPassphrase ?: ""))
    }

    // Prefill can arrive after navigation while this ViewModel owns the unsaved input state.
    LaunchedEffect(prefillWords, prefillMoneroHeight, prefillPassphrase, prefillMnemonicLanguage) {
        if (prefillWords != null) {
            viewModel.applyMnemonicPhrase(
                words = prefillWords,
                passphrase = prefillPassphrase.orEmpty(),
                moneroHeight = prefillMoneroHeight,
                language = prefillMnemonicLanguage
            )
        }
    }
    // After the prefill sync: on return from the scanner the scanned phrase must win.
    val qrScanner = navigation.rememberQrScannerLauncher(koinInject()) { scannedText ->
        when (val result = viewModel.handleScannedQrData(scannedText)) {
            is RestoreMnemonicModule.QrScanResult.Success -> {
                val wordsText = result.words.joinToString(" ")
                textState = textState.copy(
                    text = wordsText,
                    selection = TextRange(wordsText.length)
                )
                if (result.passphrase.isNotEmpty()) {
                    passphraseTextState.value = TextFieldValue(
                        text = result.passphrase,
                        selection = TextRange(result.passphrase.length)
                    )
                }
                viewModel.applyMnemonicPhrase(
                    words = result.words,
                    passphrase = result.passphrase,
                    moneroHeight = result.moneroHeight,
                    language = result.language
                )

                // Update shared state and navigate to advanced if passphrase present
                if (result.passphrase.isNotEmpty() && !advanced) {
                    mainViewModel.setPrefillData(
                        result.words,
                        result.passphrase,
                        result.moneroHeight,
                        result.language
                    )
                    openRestoreAdvanced?.invoke()
                }
            }
            is RestoreMnemonicModule.QrScanResult.PlainText -> {
                textState = textState.copy(
                    text = result.text,
                    selection = TextRange(result.text.length)
                )
                viewModel.onEnterMnemonicPhrase(result.text, result.text.length)
            }
            is RestoreMnemonicModule.QrScanResult.Error -> {
                hudMessenger.showError(result.message)
            }
        }
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
    @OptIn(ExperimentalComposeUiApi::class)
    val keyboardController = LocalSoftwareKeyboardController.current
    var showLanguageSelectorDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

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

    if (showDatePicker) {
        SelectDateBottomSheet(
            initialDateMillis = null,
            minDateMillis = restoreGenesisDateMillis(BlockchainType.Monero),
            maxDateMillis = restoreMaxDateMillis(),
            onDateSelect = viewModel::onDatePicked,
            onDismiss = { showDatePicker = false }
        )
    }

    Scaffold(
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = {
            AppBar(
                title = if (advanced) stringResource(Res.string.Restore_Advanced_Title) else stringResource(
                    Res.string.ManageAccounts_ImportWallet
                ),
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
        }) { paddingValues ->
        Column(Modifier.padding(top = paddingValues.calculateTopPadding())) {
            val state = rememberScrollState()
            LaunchedEffect(uiState.errorHeight) {
                if (uiState.errorHeight != null) {
                    state.animateScrollTo(state.maxValue)
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .verticalScroll(state)
            ) {
                Spacer(Modifier.height(12.dp))

                HeaderText(stringResource(Res.string.ManageAccount_Name))
                FormsInput(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    initial = viewModel.accountName,
                    pasteEnabled = false,
                    hint = viewModel.defaultName,
                    onValueChange = viewModel::onEnterName
                )
                Spacer(Modifier.height(16.dp))
                MoneroMode(uiState.isMoneroMnemonic, viewModel::onToggleMoneroMnemonic)
                Spacer(Modifier.height(16.dp))
                if (advanced) {
                    RestoreByMenu(restoreMenuViewModel)
                    Spacer(Modifier.height(32.dp))
                }

                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                        .background(ComposeAppTheme.colors.surfacePrimary),
                ) {
                    MnemonicLanguageCell(
                        language = uiState.language,
                        showLanguageSelectorDialog = {
                            showLanguageSelectorDialog = true
                        },
                        enabled = !uiState.isMoneroMnemonic
                    )
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = ComposeAppTheme.colors.borderDivider
                    )

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

                if (!advanced) {
                    if (uiState.isMoneroMnemonic) {
                        Spacer(Modifier.height(16.dp))
                        HeaderText(stringResource(Res.string.restoreheight_title))
                        RestoreHeightInput(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            initial = uiState.height,
                            hint = stringResource(Res.string.restoreheight_hint),
                            error = uiState.errorHeight,
                            pasteEnabled = false,
                            onValueChange = viewModel::onChangeHeightText,
                            onCalendarClick = { showDatePicker = true },
                        )
                        InfoText(text = stringResource(Res.string.select_date_description))
                    }
                }
                Spacer(Modifier.height(32.dp))

                if (advanced) {
                    BottomSection(
                        viewModel = viewModel,
                        uiState = uiState,
                        openNonStandardRestore = openNonStandardRestore,
                        passphraseTextState = passphraseTextState
                    )
                } else {
                    if (!uiState.isMoneroMnemonic) {
                        CellSingleLineLawrenceSection {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable {
                                        openRestoreAdvanced?.invoke()
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

            if (isMnemonicPhraseInputFocused && keyboardState == Keyboard.Opened) {
                SuggestionsBar(
                    modifier = Modifier
                        .imePadding()
                        .background(Color.Green),
                    wordSuggestions = uiState.wordSuggestions
                ) { wordItem, suggestion ->
                    val currentText = textState.text

                    // Guard: stale suggestion for changed text
                    if (wordItem.range.last > currentText.length) return@SuggestionsBar

                    hudMessenger.vibrate()

                    val cursorIndex = wordItem.range.first + suggestion.length + 1
                    var text = currentText.replaceRange(wordItem.range, suggestion)

                    if (text.length < cursorIndex) {
                        text = "$text "
                    }

                    textState = TextFieldValue(
                        text = text,
                        selection = TextRange(cursorIndex)
                    )

                    viewModel.onEnterMnemonicPhrase(text, cursorIndex)
                }
            } else {
                Spacer(modifier = Modifier.imePadding())
            }
        }

        uiState.accountType?.let { accountType ->
            if (accountType is AccountType.MnemonicMonero) {
                onFinish()
            } else {
                mainViewModel.setAccountData(accountType, viewModel.accountName, true, false)
                openSelectCoins.invoke()
                viewModel.onSelectCoinsShown()
            }
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
}

@Composable
private fun BottomSection(
    viewModel: RestoreMnemonicViewModel,
    uiState: RestoreMnemonicModule.UiState,
    openNonStandardRestore: () -> Unit,
    passphraseTextState: MutableState<TextFieldValue>
) {
    CellUniversalLawrenceSection(
        listOf {
            PassphraseCell(
                enabled = uiState.passphraseEnabled,
                onCheckedChange = viewModel::onTogglePassphrase
            )
        }
    )

    if (uiState.passphraseEnabled) {
        Spacer(modifier = Modifier.height(24.dp))
        FormsInputPassword(
            modifier = Modifier.padding(horizontal = 16.dp),
            hint = stringResource(Res.string.Passphrase),
            state = uiState.passphraseError?.let { DataState.Error(Exception(it)) },
            textState = passphraseTextState,
            onValueChange = viewModel::onEnterPassphrase,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        Spacer(modifier = Modifier.height(16.dp))
        TextImportantWarning(
            modifier = Modifier.padding(horizontal = 16.dp),
            text = stringResource(Res.string.Restore_PassphraseDescription)
        )
    }

    Spacer(Modifier.height(32.dp))

    CellSingleLineLawrenceSection {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .clickable {
                    openNonStandardRestore.invoke()
                }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            body_leah(text = stringResource(Res.string.Restore_NonStandardRestore))
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

@Composable
internal fun ScanQrButton(onClick: () -> Unit) {
    if (!qrScannerAvailable()) return
    ButtonSecondaryCircle(
        modifier = Modifier.padding(end = 8.dp),
        icon = Res.drawable.ic_qr_scan_20,
        onClick = onClick
    )
}

@Composable
fun SuggestionsBar(
    modifier: Modifier = Modifier,
    wordSuggestions: RestoreMnemonicModule.WordSuggestions?,
    onClick: (RestoreMnemonicModule.WordItem, String) -> Unit
) {
    Box(modifier = modifier) {
        BoxTyler44(borderTop = true) {
            if (wordSuggestions != null) {
                LazyRow(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(wordSuggestions.options) { suggestion ->
                        val wordItem = wordSuggestions.wordItem
                        ButtonSecondary(
                            onClick = {
                                onClick.invoke(wordItem, suggestion)
                            }
                        ) {
                            subhead2_leah(text = suggestion)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                }
            } else {
                Icon(
                    modifier = Modifier.align(Alignment.Center),
                    painter = painterResource(Res.drawable.ic_more_24),
                    tint = ComposeAppTheme.colors.iconSecondary,
                    contentDescription = null
                )
            }
        }
    }
}

@Composable
fun MoneroMode(enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    RowUniversal(
        modifier = Modifier.padding(horizontal = 16.dp),
        onClick = { onCheckedChange(!enabled) },
    ) {
        body_leah(
            text = stringResource(Res.string.monero_restore_seed),
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
