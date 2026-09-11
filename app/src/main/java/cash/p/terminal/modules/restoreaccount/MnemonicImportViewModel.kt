package cash.p.terminal.modules.restoreaccount

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import cash.p.terminal.core.IAccountFactory
import cash.p.terminal.core.managers.SeedPhraseQrCrypto
import cash.p.terminal.core.managers.toSeedQrErrorStringRes
import cash.p.terminal.modules.restoreaccount.restoremnemonic.RestoreMnemonicModule
import cash.p.terminal.strings.helpers.Translator
import cash.p.terminal.wallet.AccountType
import io.horizontalsystems.core.IThirdKeyboard
import io.horizontalsystems.core.ViewModelUiState
import io.horizontalsystems.hdwalletkit.Language

abstract class MnemonicImportViewModel<S>(
    protected val accountFactory: IAccountFactory,
    private val thirdKeyboardStorage: IThirdKeyboard,
    private val seedPhraseQrCrypto: SeedPhraseQrCrypto
) : ViewModelUiState<S>() {
    private var onDraftChanged: ((MnemonicImportDraft) -> Unit)? = null
    var draft = MnemonicImportDraft()
        protected set(value) {
            field = value
            onDraftChanged?.invoke(value)
        }

    fun bindDraft(value: MnemonicImportDraft, onChange: (MnemonicImportDraft) -> Unit): () -> Unit {
        if (draft != value) applyDraft(value)
        onDraftChanged = onChange
        return { onDraftChanged = null }
    }
    protected var accountType: AccountType? = null
    protected var error: String? = null
    protected var passphraseError: String? = null
    val defaultName = accountFactory.getNextAccountName()
    val accountName: String get() = draft.accountName.ifBlank { defaultName }
    val isThirdPartyKeyboardAllowed: Boolean get() = thirdKeyboardStorage.isThirdPartyKeyboardAllowed

    protected abstract fun processText()

    fun applyDraft(value: MnemonicImportDraft) {
        draft = value
        invalidateResult()
        processText()
        emitState()
    }

    fun onEnterName(name: String) = applyDraft(draft.copy(accountName = name))
    fun onEnterPassphrase(passphrase: String) = applyDraft(draft.copy(passphrase = passphrase))
    fun onTogglePassphrase(enabled: Boolean) = applyDraft(draft.copy(passphraseEnabled = enabled, passphrase = ""))
    fun onToggleLegacy(enabled: Boolean) = applyDraft(draft.selectLegacy(enabled))
    fun onEnterMnemonicPhrase(text: String, cursorPosition: Int, selectionStart: Int = cursorPosition) =
        applyDraft(draft.editText(text, cursorPosition, selectionStart))

    fun setMnemonicLanguage(language: Language) {
        if (!draft.isMoneroMnemonic) applyDraft(draft.copy(language = language))
    }

    fun onSelectCoinsShown() {
        accountType = null
        emitState()
    }

    fun onAllowThirdPartyKeyboard() {
        thirdKeyboardStorage.isThirdPartyKeyboardAllowed = true
    }

    fun handleScannedQrData(scannedText: String): RestoreMnemonicModule.QrScanResult {
        if (!scannedText.startsWith(SeedPhraseQrCrypto.QR_PREFIX)) {
            return RestoreMnemonicModule.QrScanResult.PlainText(scannedText)
        }

        return seedPhraseQrCrypto.decrypt(scannedText).fold(
            onSuccess = { decrypted ->
                RestoreMnemonicModule.QrScanResult.Success(MnemonicImportDraft.decoded(decrypted))
            },
            onFailure = { error ->
                RestoreMnemonicModule.QrScanResult.Error(
                    Translator.getString(error.toSeedQrErrorStringRes())
                )
            }
        )
    }

    fun applyScannedQrData(text: String): MnemonicImportDraft? {
        val value = when (val result = handleScannedQrData(text)) {
            is RestoreMnemonicModule.QrScanResult.Success -> result.draft
            is RestoreMnemonicModule.QrScanResult.PlainText -> MnemonicImportDraft.manual(result.text)
            is RestoreMnemonicModule.QrScanResult.Error -> {
                invalidateResult()
                error = result.message
                emitState()
                return null
            }
        }
        applyDraft(value)
        return draft
    }

    protected fun invalidateResult() {
        accountType = null
        error = null
        passphraseError = null
    }
}

@Composable
fun rememberMnemonicScanner(
    viewModel: MnemonicImportViewModel<*>,
    owner: RestoreViewModel,
    onApply: (MnemonicImportDraft) -> Unit,
): (String) -> Unit {
    val unbind = remember(viewModel, owner) { viewModel.bindDraft(owner.mnemonicDraft, owner::setDraft) }
    DisposableEffect(viewModel, owner) { onDispose(unbind) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val applyToEditor by rememberUpdatedState(onApply)
    LaunchedEffect(viewModel, owner, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            owner.scannedText.collect {
                owner.takeScannedText()?.let { text ->
                    viewModel.applyScannedQrData(text)?.let(applyToEditor)
                }
            }
        }
    }
    return owner::onScannedText
}
