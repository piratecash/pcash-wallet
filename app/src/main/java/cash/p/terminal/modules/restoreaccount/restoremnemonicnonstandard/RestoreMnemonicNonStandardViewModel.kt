package cash.p.terminal.modules.restoreaccount.restoremnemonicnonstandard

import cash.p.terminal.modules.restoreaccount.MnemonicImportViewModel
import cash.p.terminal.modules.restoreaccount.MnemonicInput
import cash.p.terminal.core.managers.SeedPhraseQrCrypto
import cash.p.terminal.R
import cash.p.terminal.strings.helpers.Translator
import cash.p.terminal.core.IAccountFactory
import cash.p.terminal.core.managers.WordsManager
import cash.p.terminal.modules.restoreaccount.restoremnemonic.RestoreMnemonicModule
import cash.p.terminal.modules.restoreaccount.restoremnemonicnonstandard.RestoreMnemonicNonStandardModule.UiState
import io.horizontalsystems.core.IThirdKeyboard
import io.horizontalsystems.hdwalletkit.Language
import io.horizontalsystems.hdwalletkit.Mnemonic
import io.horizontalsystems.hdwalletkit.WordList

class RestoreMnemonicNonStandardViewModel(
    accountFactory: IAccountFactory,
    private val wordsManager: WordsManager,
    thirdKeyboardStorage: IThirdKeyboard,
    seedPhraseQrCrypto: SeedPhraseQrCrypto,
) : MnemonicImportViewModel<UiState>(accountFactory, thirdKeyboardStorage, seedPhraseQrCrypto) {

    val mnemonicLanguages = Language.entries.toList()

    private var wordItems: List<RestoreMnemonicModule.WordItem> = listOf()
    private var invalidWordItems: List<RestoreMnemonicModule.WordItem> = listOf()
    private var invalidWordRanges: List<IntRange> = listOf()
    private var wordSuggestions: RestoreMnemonicModule.WordSuggestions? = null

    override fun createState() = UiState(
        draft = draft,
        passphraseEnabled = draft.passphraseEnabled,
        passphraseError = passphraseError,
        invalidWordRanges = invalidWordRanges,
        error = error,
        accountType = accountType,
        wordSuggestions = wordSuggestions,
        language = draft.language,
    )

    override fun processText() {
        wordItems = draft.wordItems()
        if (draft.isJapanese) draft = draft.copy(language = Language.Japanese)
        val mnemonicWordList = WordList.wordList(draft.language)
        val analysis = MnemonicInput.analyze(wordItems, draft.cursorPosition, mnemonicWordList, false)
        invalidWordItems = analysis.invalidItems
        invalidWordRanges = analysis.invalidRanges
        wordSuggestions = analysis.suggestions
    }

    fun onProceed() {
        when {
            invalidWordItems.isNotEmpty() -> {
                invalidWordRanges = invalidWordItems.map { it.range }
            }

            wordItems.size !in (Mnemonic.EntropyStrength.values().map { it.wordCount }) -> {
                error = Translator.getString(
                    R.string.Restore_Error_MnemonicWordCount, wordItems.size
                )
            }

            !draft.preservesRaw && draft.passphraseEnabled && draft.passphrase.isBlank() -> {
                passphraseError =
                    Translator.getString(R.string.Restore_Error_EmptyPassphrase)
            }

            else -> {
                try {
                    val words = wordItems.map { it.word }
                    wordsManager.validateChecksum(words)

                    accountType = draft.accountType(nonStandard = true)
                    error = null
                } catch (checksumException: Exception) {
                    error = Translator.getString(R.string.Restore_InvalidChecksum)
                }
            }
        }

        emitState()
    }

}
