package cash.p.terminal.modules.restoreaccount.restoremnemonic

import androidx.lifecycle.viewModelScope
import cash.p.terminal.modules.restoreaccount.MnemonicImportViewModel
import cash.p.terminal.modules.restoreaccount.MnemonicInput
import cash.p.terminal.R
import cash.p.terminal.core.IAccountFactory
import cash.p.terminal.core.managers.SeedPhraseQrCrypto
import cash.p.terminal.core.managers.WalletActivator
import cash.p.terminal.core.usecase.MoneroWalletUseCase
import cash.p.terminal.core.usecase.ValidateMoneroHeightUseCase
import cash.p.terminal.core.usecase.ValidateMoneroMnemonicUseCase
import cash.p.terminal.core.utils.Bip39LanguageDetector
import cash.p.terminal.core.utils.MoneroConfig
import cash.p.terminal.modules.mnemonic.mnemonicLanguagesOrdered
import cash.p.terminal.modules.restoreaccount.restoremnemonic.RestoreMnemonicModule.UiState
import cash.p.terminal.modules.restoreaccount.restoremnemonic.RestoreMnemonicModule.WordItem
import cash.p.terminal.strings.helpers.Translator
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.normalizeNFKD
import com.m2049r.xmrwallet.util.ledger.Monero
import io.horizontalsystems.core.IThirdKeyboard
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.hdwalletkit.Language
import io.horizontalsystems.hdwalletkit.Mnemonic
import io.horizontalsystems.hdwalletkit.MnemonicWordList
import io.horizontalsystems.hdwalletkit.WordList
import java.time.LocalDate
import kotlinx.coroutines.launch

class RestoreMnemonicViewModel(
    private val validateMoneroMnemonicUseCase: ValidateMoneroMnemonicUseCase,
    private val validateMoneroHeightUseCase: ValidateMoneroHeightUseCase,
    private val moneroWalletUseCase: MoneroWalletUseCase,
    private val accountManager: IAccountManager,
    private val walletActivator: WalletActivator,
    seedPhraseQrCrypto: SeedPhraseQrCrypto,
    accountFactory: IAccountFactory,
    thirdKeyboardStorage: IThirdKeyboard
) : MnemonicImportViewModel<UiState>(accountFactory, thirdKeyboardStorage, seedPhraseQrCrypto) {

    val mnemonicLanguages = mnemonicLanguagesOrdered

    private var wordItems: List<WordItem> = listOf()
    private var invalidWordItems: List<WordItem> = listOf()
    private var invalidWordRanges: List<IntRange> = listOf()
    private var errorHeight: String? = null
    private var wordSuggestions: RestoreMnemonicModule.WordSuggestions? = null
    private var mnemonicMoneroWordList = MnemonicWordList(Monero.ENGLISH_WORDS.toList(), false)

    private val mnemonicWordList: MnemonicWordList
        get() = when {
            draft.isMoneroMnemonic -> mnemonicMoneroWordList
            draft.preservesRaw -> WordList.wordList(draft.language)
            else -> WordList.wordListStrict(draft.language)
        }

    override fun createState() = UiState(
        draft = draft,
        passphraseEnabled = draft.passphraseEnabled,
        passphraseError = passphraseError,
        invalidWordRanges = invalidWordRanges,
        error = error,
        errorHeight = errorHeight,
        height = draft.height,
        isMoneroMnemonic = draft.isMoneroMnemonic,
        accountType = accountType,
        wordSuggestions = wordSuggestions,
        language = displayedLanguage,
    )

    fun onToggleMoneroMnemonic(enabled: Boolean) {
        applyDraft(draft.moneroMode(enabled))
    }

    override fun processText() {
        errorHeight = null
        wordItems = draft.wordItems()

        if (draft.isJapanese) {
            draft = draft.copy(language = Language.Japanese)
        } else if (!draft.isMoneroMnemonic && wordItems.size >= MIN_WORDS_FOR_AUTODETECT) {
            autodetectLanguage(wordItems.map { it.word })
        }

        val analysis = MnemonicInput.analyze(wordItems, draft.cursorPosition, mnemonicWordList, true)
        invalidWordItems = analysis.invalidItems
        invalidWordRanges = analysis.invalidRanges
        wordSuggestions = analysis.suggestions
    }

    fun onChangeHeightText(text: String) {
        draft = draft.copy(height = text)
        invalidateResult()
        emitState()
    }

    fun onDatePicked(date: LocalDate) {
        invalidateResult()
        val pickedHeight = validateMoneroHeightUseCase.getHeight(date)
        if (pickedHeight == -1L) {
            errorHeight = Translator.getString(R.string.invalid_height_format)
        } else {
            draft = draft.copy(height = pickedHeight.toString())
            errorHeight = null
        }

        emitState()
    }

    fun onProceed() = viewModelScope.launch {
        if (validateInput()) {
            try {
                restoreAccountType()
                if (accountType is AccountType.MnemonicMonero) finishRestoringMoneroAccount()
            } catch (_: Exception) {
                error = Translator.getString(R.string.Restore_InvalidChecksum)
            }
        }
        emitState()
    }

    private fun validateInput(): Boolean {
        when {
            invalidWordItems.isNotEmpty() -> invalidWordRanges = invalidWordItems.map { it.range }
            draft.isMoneroMnemonic && wordItems.size != MoneroConfig.WORD_COUNT ->
                error = Translator.getString(R.string.Restore_Error_MnemonicWordCount_monero, wordItems.size)
            draft.isMoneroMnemonic && validateMoneroHeightUseCase(draft.height) == -1L ->
                errorHeight = Translator.getString(R.string.invalid_height_format)
            !draft.isMoneroMnemonic && wordItems.size !in Mnemonic.EntropyStrength.entries.map { it.wordCount } ->
                error = Translator.getString(R.string.Restore_Error_MnemonicWordCount, wordItems.size)
            !draft.preservesRaw && draft.passphraseEnabled && draft.passphrase.isBlank() ->
                passphraseError = Translator.getString(R.string.Restore_Error_EmptyPassphrase)
            else -> return true
        }
        return false
    }

    private suspend fun restoreAccountType() {
        val words = wordItems.map { it.word.normalizeNFKD() }
        validateMoneroMnemonicUseCase(words, draft.isMoneroMnemonic, strict = !draft.preservesRaw)
        accountType = if (draft.isMoneroMnemonic) {
            moneroWalletUseCase.restore(words = words, height = validateMoneroHeightUseCase(draft.height))
        } else {
            draft.accountType(nonStandard = false)
        }
        error = if (accountType == null) Translator.getString(R.string.monero_restore_error) else null
        errorHeight = null
    }

    private suspend fun finishRestoringMoneroAccount() {
        val accountType = accountType ?: return

        val account = accountFactory.account(
            name = accountName,
            type = accountType,
            origin = AccountOrigin.Restored,
            backedUp = true,
            fileBackedUp = false,
        )

        accountManager.save(account)
        walletActivator.activateWalletsSuspended(
            account,
            listOf(TokenQuery(BlockchainType.Monero, TokenType.Native))
        )
    }

    private fun setNormalMnemonicLanguage(language: Language) {
        draft = draft.copy(language = language)
    }

    private fun autodetectLanguage(words: List<String>) {
        val detected = Bip39LanguageDetector.detectExact(words)
        if (detected.isEmpty() || draft.language in detected) return
        setNormalMnemonicLanguage(detected.first())
    }

    private val displayedLanguage: Language
        get() = if (draft.isMoneroMnemonic) Language.English else draft.language

    companion object {
        // Single-word input is too ambiguous (e.g. "ábaco" exists in Spanish only, but
        // "abandon" prefixes match many wordlists) — only autodetect on multi-word input.
        private const val MIN_WORDS_FOR_AUTODETECT = 2
    }
}
