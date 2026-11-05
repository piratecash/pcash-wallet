package cash.p.terminal.modules.restoreaccount.restoremnemonic

import cash.p.terminal.feature.wallet.qrScannerAvailable
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.createaccount.PassphraseCell
import cash.p.terminal.modules.mnemonic.MnemonicLanguageCell
import cash.p.terminal.modules.mnemonic.MnemonicLanguageSelectorDialog
import cash.p.terminal.modules.restoreaccount.RestoreViewModel
import cash.p.terminal.modules.restoreaccount.openShamirRestore
import cash.p.terminal.modules.restoreaccount.restoremenu.RestoreByMenu
import cash.p.terminal.modules.restoreaccount.restoremenu.RestoreMenuViewModel
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.rememberQrScannerLauncher
import cash.p.terminal.resources.Button_Next
import cash.p.terminal.resources.Res
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui.compose.components.BoxTyler44
import cash.p.terminal.ui.compose.components.ThirdPartyKeyboardWarning
import cash.p.terminal.ui.compose.components.rememberThirdPartyKeyboardWarningState
import cash.p.terminal.ui.compose.components.FormsInput
import cash.p.terminal.ui.compose.components.RestoreHeightInput
import cash.p.terminal.ui.compose.components.SelectDateBottomSheet
import cash.p.terminal.ui.compose.components.restoreGenesisDateMillis
import cash.p.terminal.ui.compose.components.restoreMaxDateMillis
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonSecondary
import cash.p.terminal.ui_compose.components.ButtonSecondaryCircle
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
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.subhead2_leah
import cash.p.terminal.ui_compose.components.caption_lucian
import cash.p.terminal.ui_compose.entities.DataState
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
import cash.p.terminal.resources.Restore_RecoveryPhrase
import cash.p.terminal.resources.ic_arrow_right
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
    val keyboardWarning = rememberThirdPartyKeyboardWarningState(viewModel)
    val hudMessenger = rememberHudMessenger()
    val scannerTitle = stringResource(Res.string.Restore_RecoveryPhrase)

    // Build initial text from prefill data (words only, passphrase handled separately)
    val initialText = remember(prefillWords) {
        prefillWords?.joinToString(" ") ?: ""
    }

    var textState by rememberSaveable(initialText, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialText, TextRange(initialText.length)))
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
            is RestoreMnemonicModule.QrScanResult.Shamir -> navigation.openShamirRestore(result.prefill)

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
    var isMnemonicPhraseInputFocused by remember { mutableStateOf(false) }
    val onPhraseChange: (TextFieldValue) -> Unit = {
        textState = it
        viewModel.onEnterMnemonicPhrase(it.text, it.selection.max)
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

                PhraseInputField(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    textState = textState,
                    invalidWordRanges = uiState.invalidWordRanges,
                    isError = uiState.error != null,
                    onValueChange = {
                        onPhraseChange(it)
                        keyboardWarning.onSecretInput()
                    },
                    onTextReplace = onPhraseChange,
                    onScanClick = { qrScanner.open(scannerTitle) },
                    onFocusChange = { isMnemonicPhraseInputFocused = it },
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

            PhraseSuggestions(isMnemonicPhraseInputFocused, uiState.wordSuggestions) { wordItem, suggestion ->
                textState.withSuggestion(wordItem, suggestion)?.let {
                    hudMessenger.vibrate()
                    onPhraseChange(it)
                }
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

        ThirdPartyKeyboardWarning(keyboardWarning)
    }
}

@Composable
private fun BottomSection(
    viewModel: RestoreMnemonicViewModel,
    uiState: RestoreMnemonicModule.UiState,
    openNonStandardRestore: () -> Unit,
) {
    RestorePassphraseSection(
        enabled = uiState.passphraseEnabled,
        passphrase = uiState.passphrase,
        error = uiState.passphraseError,
        onToggle = viewModel::onTogglePassphrase,
        onChange = viewModel::onEnterPassphrase,
    )

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
internal fun RestorePassphraseSection(
    enabled: Boolean,
    passphrase: String,
    error: String?,
    onToggle: (Boolean) -> Unit,
    onChange: (String) -> Unit,
    editable: Boolean = true,
) = Column {
    val textState = rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(passphrase, TextRange(passphrase.length)))
    }
    // The view model may replace the passphrase (QR prefill, toggle reset); typing already matches it.
    LaunchedEffect(passphrase) {
        if (textState.value.text != passphrase) {
            textState.value = TextFieldValue(passphrase, TextRange(passphrase.length))
        }
    }

    CellUniversalLawrenceSection(
        listOf {
            PassphraseCell(
                enabled = enabled,
                onCheckedChange = onToggle
            )
        }
    )

    if (enabled) {
        Spacer(modifier = Modifier.height(24.dp))
        FormsInputPassword(
            modifier = Modifier.padding(horizontal = 16.dp),
            hint = stringResource(Res.string.Passphrase),
            state = error?.let { DataState.Error(Exception(it)) },
            textState = textState,
            onValueChange = onChange,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            enabled = editable,
        )
        Spacer(modifier = Modifier.height(16.dp))
        TextImportantWarning(
            modifier = Modifier.padding(horizontal = 16.dp),
            text = stringResource(Res.string.Restore_PassphraseDescription)
        )
    }
}

@Composable
internal fun ScanQrButton(onClick: () -> Unit) {
    // Previews run without Koin, which the availability check needs.
    if (LocalInspectionMode.current || !qrScannerAvailable()) return
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
