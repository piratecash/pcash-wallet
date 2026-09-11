package cash.p.terminal.modules.restoreaccount.restoremnemonic

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.activity.compose.BackHandler
import cash.p.terminal.modules.restoreaccount.rememberMnemonicScanner
import cash.p.terminal.modules.restoreaccount.MnemonicImportDraft
import cash.p.terminal.modules.mnemonic.JapaneseLegacyCell
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
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
import androidx.navigation.findNavController
import cash.p.terminal.R
import cash.p.terminal.core.launchAfterClearingFocus
import cash.p.terminal.core.utils.Utils
import cash.p.terminal.modules.createaccount.PassphraseCell
import cash.p.terminal.modules.mnemonic.MnemonicLanguageCell
import cash.p.terminal.modules.mnemonic.MnemonicLanguageSelectorDialog
import cash.p.terminal.modules.restoreaccount.RestoreViewModel
import cash.p.terminal.navigation.openQrScanner
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
import cash.p.terminal.ui_compose.components.HudHelper
import cash.p.terminal.ui_compose.components.InfoText
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.TextImportantWarning
import cash.p.terminal.ui_compose.components.body_grey50
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.captionSB_leah
import cash.p.terminal.ui_compose.components.caption_lucian
import cash.p.terminal.ui_compose.entities.DataState
import cash.p.terminal.ui_compose.theme.ColoredTextStyle
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.AccountType
import io.horizontalsystems.core.entities.BlockchainType
import cash.p.terminal.modules.mnemonic.mnemonicLanguagesOrdered
import io.horizontalsystems.hdwalletkit.Language
import java.time.LocalDate
import timber.log.Timber
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

private data class RestorePhraseActions(
    val onEnterName: (String) -> Unit,
    val onEnterPhrase: (String, Int, Int) -> Unit,
    val onEnterPassphrase: (String) -> Unit,
    val onTogglePassphrase: (Boolean) -> Unit,
    val onToggleMonero: (Boolean) -> Unit,
    val onToggleLegacy: (Boolean) -> Unit,
    val onLanguage: (Language) -> Unit,
    val onHeight: (String) -> Unit,
    val onDate: (LocalDate) -> Unit,
    val onScan: (String) -> Unit,
    val onClear: () -> Unit,
    val onProceed: () -> Unit,
    val shouldWarnAboutKeyboard: () -> Boolean,
    val onAllowKeyboard: () -> Unit,
)

private data class RestorePhraseNavigation(
    val onBack: () -> Unit,
    val openAdvanced: () -> Unit,
    val openNonStandard: () -> Unit,
)

private class RestorePhraseEditor(draft: MnemonicImportDraft) {
    var text by mutableStateOf(TextFieldValue(draft.text, TextRange(draft.selectionStart, draft.cursorPosition)))
    val passphrase = mutableStateOf(TextFieldValue(draft.passphrase))
    var focused by mutableStateOf(false)
    var showKeyboardWarning by mutableStateOf(false)

    fun enter(value: TextFieldValue, onEnterPhrase: (String, Int, Int) -> Unit) {
        text = value
        onEnterPhrase(value.text, value.selection.end, value.selection.start)
    }

    fun replace(draft: MnemonicImportDraft) {
        text = TextFieldValue(draft.text, TextRange(draft.selectionStart, draft.cursorPosition))
        passphrase.value = TextFieldValue(draft.passphrase)
    }
}

@Composable
fun RestorePhrase(
    advanced: Boolean,
    mainViewModel: RestoreViewModel,
    openSelectCoins: () -> Unit,
    openNonStandardRestore: () -> Unit,
    onBackClick: () -> Unit,
    onFinish: () -> Unit,
    openRestoreAdvanced: (() -> Unit)? = null,
    restoreMenu: @Composable () -> Unit = {},
) {
    val viewModel = koinViewModel<RestoreMnemonicViewModel>()
    val context = LocalContext.current
    val editor = remember(viewModel) { RestorePhraseEditor(mainViewModel.mnemonicDraft) }
    val onScan = rememberMnemonicScanner(viewModel, mainViewModel) { draft ->
        editor.replace(draft)
        if (!advanced && draft.passphraseEnabled) openRestoreAdvanced?.invoke()
    }
    val uiState = viewModel.uiState
    LaunchedEffect(uiState.draft.passphrase) {
        if (editor.passphrase.value.text != uiState.draft.passphrase) {
            editor.passphrase.value = TextFieldValue(uiState.draft.passphrase)
        }
    }
    val saveDraft = { mainViewModel.setDraft(viewModel.draft) }
    val onBack = { saveDraft(); onBackClick() }
    BackHandler(onBack = onBack)
    val actions = viewModel.phraseActions(onScan) {
        !viewModel.isThirdPartyKeyboardAllowed && Utils.isUsingCustomKeyboard(context)
    }
    RestorePhraseContent(
        advanced = advanced, uiState = uiState, editor = editor, actions = actions,
        defaultName = viewModel.defaultName,
        navigation = RestorePhraseNavigation(onBack,
            { saveDraft(); openRestoreAdvanced?.invoke() }, { saveDraft(); openNonStandardRestore() }),
        restoreMenu = restoreMenu
    )
    uiState.accountType?.let { accountType ->
        if (accountType is AccountType.MnemonicMonero) {
            onFinish()
        } else {
            mainViewModel.setAccountData(accountType, viewModel.accountName, true, false)
            openSelectCoins()
            viewModel.onSelectCoinsShown()
        }
    }
}

private fun RestoreMnemonicViewModel.phraseActions(
    onScan: (String) -> Unit,
    shouldWarnAboutKeyboard: () -> Boolean,
) = RestorePhraseActions(
    onEnterName = ::onEnterName,
    onEnterPhrase = ::onEnterMnemonicPhrase,
    onEnterPassphrase = ::onEnterPassphrase,
    onTogglePassphrase = ::onTogglePassphrase,
    onToggleMonero = ::onToggleMoneroMnemonic,
    onToggleLegacy = ::onToggleLegacy,
    onLanguage = ::setMnemonicLanguage,
    onHeight = ::onChangeHeightText,
    onDate = ::onDatePicked,
    onScan = onScan,
    onClear = { applyDraft(MnemonicImportDraft()) },
    onProceed = ::onProceed,
    shouldWarnAboutKeyboard = shouldWarnAboutKeyboard,
    onAllowKeyboard = ::onAllowThirdPartyKeyboard,
)

@Composable
private fun RestorePhraseContent(
    advanced: Boolean,
    uiState: RestoreMnemonicModule.UiState,
    editor: RestorePhraseEditor,
    actions: RestorePhraseActions,
    defaultName: String,
    navigation: RestorePhraseNavigation,
    restoreMenu: @Composable () -> Unit,
) {
    Scaffold(
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = { RestorePhraseAppBar(advanced, navigation.onBack, actions.onProceed) }
    ) { paddingValues ->
        Column(Modifier.padding(top = paddingValues.calculateTopPadding())) {
            val scrollState = rememberScrollState()
            LaunchedEffect(uiState.errorHeight) {
                if (uiState.errorHeight != null) scrollState.animateScrollTo(scrollState.maxValue)
            }
            Column(Modifier.fillMaxSize().weight(1f).verticalScroll(scrollState)) {
                RestorePhraseForm(advanced, uiState, editor, actions, defaultName, navigation, restoreMenu)
            }
            RestoreSuggestions(editor, uiState.wordSuggestions, actions.onEnterPhrase)
        }
        RestoreKeyboardWarning(editor, actions.onAllowKeyboard)
    }
}

@Composable
private fun RestorePhraseAppBar(advanced: Boolean, onBack: () -> Unit, onProceed: () -> Unit) {
    AppBar(
        title = stringResource(if (advanced) R.string.Restore_Advanced_Title else R.string.ManageAccounts_ImportWallet),
        navigationIcon = { HsBackButton(onClick = onBack) },
        menuItems = listOf(MenuItem(title = TranslatableString.ResString(R.string.Button_Next), onClick = onProceed))
    )
}

@Composable
private fun ColumnScope.RestorePhraseForm(
    advanced: Boolean,
    uiState: RestoreMnemonicModule.UiState,
    editor: RestorePhraseEditor,
    actions: RestorePhraseActions,
    defaultName: String,
    navigation: RestorePhraseNavigation,
    restoreMenu: @Composable () -> Unit,
) {
    Spacer(Modifier.height(12.dp))
    HeaderText(stringResource(R.string.ManageAccount_Name))
    FormsInput(
        modifier = Modifier.padding(horizontal = 16.dp), initial = uiState.draft.accountName.ifBlank { defaultName },
        pasteEnabled = false, hint = defaultName, onValueChange = actions.onEnterName
    )
    Spacer(Modifier.height(16.dp))
    MoneroMode(uiState.isMoneroMnemonic, actions.onToggleMonero)
    Spacer(Modifier.height(16.dp))
    if (advanced) {
        restoreMenu()
        Spacer(Modifier.height(32.dp))
    }
    MnemonicEditor(uiState, editor, actions)
    Spacer(Modifier.height(8.dp))
    uiState.error?.let { caption_lucian(modifier = Modifier.padding(horizontal = 32.dp), text = it) }
    if (!advanced && uiState.isMoneroMnemonic) RestoreMoneroHeight(uiState, actions.onHeight, actions.onDate)
    Spacer(Modifier.height(32.dp))
    RestorePhraseOptions(advanced, uiState, editor, actions, navigation.openAdvanced, navigation.openNonStandard)
    JapaneseLegacyCell(uiState.draft, actions.onToggleLegacy)
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun ColumnScope.RestorePhraseOptions(
    advanced: Boolean,
    uiState: RestoreMnemonicModule.UiState,
    editor: RestorePhraseEditor,
    actions: RestorePhraseActions,
    openAdvanced: () -> Unit,
    openNonStandard: () -> Unit,
) {
    if (!advanced && uiState.isMoneroMnemonic) return
    if (advanced) {
        BottomSection(actions, uiState, openNonStandard, editor.passphrase)
    } else {
        RestoreNavigationCell(stringResource(R.string.Button_Advanced), openAdvanced)
    }
    Spacer(Modifier.height(if (uiState.draft.isJapanese) 16.dp else 32.dp))
}

@Composable
private fun MnemonicEditor(
    uiState: RestoreMnemonicModule.UiState,
    editor: RestorePhraseEditor,
    actions: RestorePhraseActions,
) {
    val borderColor = if (uiState.error != null) ComposeAppTheme.colors.red50 else ComposeAppTheme.colors.steel20
    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp)).background(ComposeAppTheme.colors.lawrence)
    ) {
        RestoreLanguageSelector(uiState, actions.onLanguage)
        HorizontalDivider(thickness = 1.dp, color = ComposeAppTheme.colors.steel10)
        MnemonicTextInput(uiState, editor, actions)
        MnemonicEditorButtons(editor, actions)
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun MnemonicTextInput(
    uiState: RestoreMnemonicModule.UiState,
    editor: RestorePhraseEditor,
    actions: RestorePhraseActions,
) {
    val style = SpanStyle(color = ComposeAppTheme.colors.lucian, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, letterSpacing = 0.sp)
    BasicTextField(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged {
                editor.focused = it.isFocused
            }
            .defaultMinSize(minHeight = 68.dp)
            .padding(start = 16.dp, end = 16.dp, top = 12.dp),
        enabled = true,
        value = editor.text,
        onValueChange = {
            editor.enter(it, actions.onEnterPhrase)
            editor.showKeyboardWarning = actions.shouldWarnAboutKeyboard()
        },
        textStyle = ColoredTextStyle(
            color = ComposeAppTheme.colors.leah,
            textStyle = ComposeAppTheme.typography.body
        ),
        maxLines = 6,
        cursorBrush = SolidColor(ComposeAppTheme.colors.jacob),
        visualTransformation = { highlightInvalidWords(it, uiState.invalidWordRanges, style) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        decorationBox = { innerTextField ->
            if (editor.text.text.isEmpty()) {
                body_grey50(
                    stringResource(R.string.Restore_PhraseHint),
                    overflow = TextOverflow.Ellipsis,
                )
            }
            innerTextField()
        },
    )
}

private fun highlightInvalidWords(text: AnnotatedString, ranges: List<IntRange>, style: SpanStyle): TransformedText =
    try {
        val annotatedString = buildAnnotatedString {
            append(text.text)

            ranges.forEach { range ->
                addStyle(style = style, range.first, range.last + 1)
            }
        }
        TransformedText(annotatedString, OffsetMapping.Identity)
    } catch (error: Throwable) {
        Timber.e(error, "Unable to highlight invalid mnemonic ranges")
        TransformedText(text, OffsetMapping.Identity)
    }

@Composable
private fun MnemonicEditorButtons(
    editor: RestorePhraseEditor,
    actions: RestorePhraseActions,
) {
    Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically) {
        if (editor.text.text.isNotEmpty()) {
            ButtonSecondaryCircle(modifier = Modifier.padding(end = 16.dp), icon = R.drawable.ic_delete_20,
                onClick = { editor.replace(MnemonicImportDraft()); actions.onClear() })
        } else {
            MnemonicScanButton(actions.onScan)
            val clipboardManager = LocalClipboardManager.current
            ButtonSecondaryDefault(
                modifier = Modifier.padding(end = 16.dp), title = stringResource(R.string.Send_Button_Paste),
                onClick = {
                    clipboardManager.getText()?.text?.let { text ->
                        val value = editor.text.copy(text = text, selection = TextRange(text.length))
                        editor.enter(value, actions.onEnterPhrase)
                    }
                }
            )
        }
    }
}

@Composable
private fun MnemonicScanButton(
    onScan: (String) -> Unit,
) {
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val scannerTitle = stringResource(R.string.Restore_RecoveryPhrase)
    ButtonSecondaryCircle(modifier = Modifier.padding(end = 8.dp), icon = R.drawable.ic_qr_scan_20,
        onClick = {
            coroutineScope.launchAfterClearingFocus(focusManager) {
                view.findNavController().openQrScanner(scannerTitle, onResult = onScan)
            }
        }
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun RestoreLanguageSelector(uiState: RestoreMnemonicModule.UiState, onLanguage: (Language) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    MnemonicLanguageCell(language = uiState.language, showLanguageSelectorDialog = { showDialog = true },
        enabled = !uiState.isMoneroMnemonic)
    if (showDialog) {
        MnemonicLanguageSelectorDialog(
            languages = mnemonicLanguagesOrdered, selectedLanguage = uiState.language,
            onDismissRequest = {
                coroutineScope.launch {
                    showDialog = false
                    delay(300)
                    keyboardController?.show()
                }
            },
            onSelectLanguage = onLanguage
        )
    }
}

@Composable
private fun ColumnScope.RestoreMoneroHeight(
    uiState: RestoreMnemonicModule.UiState,
    onHeight: (String) -> Unit,
    onDate: (LocalDate) -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    Spacer(Modifier.height(16.dp))
    HeaderText(stringResource(R.string.restoreheight_title))
    RestoreHeightInput(modifier = Modifier.padding(horizontal = 16.dp), initial = uiState.height,
        hint = stringResource(R.string.restoreheight_hint), error = uiState.errorHeight, pasteEnabled = false,
        onValueChange = onHeight, onCalendarClick = { showDatePicker = true })
    InfoText(text = stringResource(R.string.select_date_description))
    if (showDatePicker) {
        SelectDateBottomSheet(initialDateMillis = null, minDateMillis = restoreGenesisDateMillis(BlockchainType.Monero),
            maxDateMillis = restoreMaxDateMillis(), onDateSelect = onDate, onDismiss = { showDatePicker = false })
    }
}

@Composable
private fun RestoreSuggestions(
    editor: RestorePhraseEditor,
    suggestions: RestoreMnemonicModule.WordSuggestions?,
    onEnterPhrase: (String, Int, Int) -> Unit,
) {
    val keyboardState by observeKeyboardState()
    val context = LocalContext.current
    if (editor.focused && keyboardState == Keyboard.Opened) {
        SuggestionsBar(modifier = Modifier.imePadding().background(Color.Green), wordSuggestions = suggestions) {
                wordItem, suggestion ->
            val currentText = editor.text.text
            // Guard: stale suggestion for changed text
            if (wordItem.range.last > currentText.length) return@SuggestionsBar
            HudHelper.vibrate(context)
            val cursorIndex = wordItem.range.first + suggestion.length + 1
            var text = currentText.replaceRange(wordItem.range, suggestion)
            if (text.length < cursorIndex) text = "$text "
            editor.enter(TextFieldValue(text, TextRange(cursorIndex)), onEnterPhrase)
        }
    } else {
        Spacer(Modifier.imePadding())
    }
}

@Composable
private fun RestoreKeyboardWarning(editor: RestorePhraseEditor, onAllowKeyboard: () -> Unit) {
    val context = LocalContext.current
    if (editor.showKeyboardWarning) {
        CustomKeyboardWarningDialog(
            onSelect = {
                val imeManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imeManager.showInputMethodPicker()
                editor.showKeyboardWarning = false
            },
            onSkip = { onAllowKeyboard(); editor.showKeyboardWarning = false },
            onCancel = { editor.showKeyboardWarning = false }
        )
    }
}

@Composable
private fun RestoreNavigationCell(title: String, onClick: () -> Unit) {
    CellSingleLineLawrenceSection {
        Row(Modifier.fillMaxSize().clickable(onClick = onClick).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            body_leah(text = title)
            Spacer(Modifier.weight(1f))
            Image(modifier = Modifier.size(20.dp), painter = painterResource(R.drawable.ic_arrow_right),
                contentDescription = null)
        }
    }
}

@Composable
private fun ColumnScope.BottomSection(
    actions: RestorePhraseActions,
    uiState: RestoreMnemonicModule.UiState,
    openNonStandardRestore: () -> Unit,
    passphraseTextState: MutableState<TextFieldValue>
) {
    CellUniversalLawrenceSection(
        listOf {
            PassphraseCell(
                enabled = uiState.passphraseEnabled,
                onCheckedChange = actions.onTogglePassphrase
            )
        }
    )

    PassphraseInput(uiState, passphraseTextState, actions.onEnterPassphrase)

    Spacer(Modifier.height(32.dp))

    RestoreNavigationCell(stringResource(R.string.Restore_NonStandardRestore), openNonStandardRestore)
}

@Composable
private fun ColumnScope.PassphraseInput(
    uiState: RestoreMnemonicModule.UiState,
    passphraseTextState: MutableState<TextFieldValue>,
    onEnterPassphrase: (String) -> Unit
) {
    if (uiState.passphraseEnabled) {
        Spacer(modifier = Modifier.height(24.dp))
        FormsInputPassword(
            modifier = Modifier.padding(horizontal = 16.dp),
            hint = stringResource(R.string.Passphrase),
            state = uiState.passphraseError?.let { DataState.Error(Exception(it)) },
            textState = passphraseTextState,
            onValueChange = onEnterPassphrase,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        Spacer(modifier = Modifier.height(16.dp))
        TextImportantWarning(
            modifier = Modifier.padding(horizontal = 16.dp),
            text = stringResource(R.string.Restore_PassphraseDescription)
        )
    }

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
                            captionSB_leah(text = suggestion)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                }
            } else {
                Icon(
                    modifier = Modifier.align(Alignment.Center),
                    painter = painterResource(R.drawable.ic_more_24),
                    tint = ComposeAppTheme.colors.grey,
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
            text = stringResource(R.string.monero_restore_seed),
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
