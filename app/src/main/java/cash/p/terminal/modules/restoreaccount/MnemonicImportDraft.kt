package cash.p.terminal.modules.restoreaccount

import android.os.Parcelable
import cash.p.terminal.core.managers.SeedPhraseQrCrypto
import cash.p.terminal.modules.restoreaccount.restoremnemonic.RestoreMnemonicModule.WordSuggestions
import io.horizontalsystems.hdwalletkit.MnemonicWordList
import cash.p.terminal.core.utils.Bip39LanguageDetector
import cash.p.terminal.modules.restoreaccount.restoremnemonic.RestoreMnemonicModule.WordItem
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.MnemonicDerivation
import cash.p.terminal.wallet.normalizeNFKD
import io.horizontalsystems.hdwalletkit.Language
import io.horizontalsystems.hdwalletkit.WordList
import kotlinx.parcelize.Parcelize

@Parcelize
data class MnemonicImportDraft(
    val text: String = "",
    val cursorPosition: Int = 0,
    val selectionStart: Int = cursorPosition,
    val passphrase: String = "",
    val passphraseEnabled: Boolean = false,
    val height: String = "",
    val isMoneroMnemonic: Boolean = false,
    val language: Language = Language.English,
    val derivation: MnemonicDerivation = MnemonicDerivation.Legacy,
    val source: Source = Source.Manual,
    val decodedWords: List<String>? = null,
    val accountName: String = ""
) : Parcelable {
    enum class Source { Manual, DecodedQr }

    override fun toString() = "MnemonicImportDraft(source=$source, derivation=$derivation, language=$language)"

    fun moneroMode(enabled: Boolean): MnemonicImportDraft {
        val mode = if (preservesRaw) derivation else if (!enabled && MnemonicInput.isJapanese(text))
            MnemonicDerivation.Bip39 else MnemonicDerivation.Legacy
        return copy(isMoneroMnemonic = enabled, derivation = mode)
    }

    val isJapanese: Boolean
        get() = !isMoneroMnemonic && MnemonicInput.isJapanese(text)
    val preservesRaw: Boolean
        get() = source == Source.DecodedQr

    fun editText(value: String, cursor: Int, selection: Int = cursor): MnemonicImportDraft {
        val edited = copy(text = value, cursorPosition = cursor, selectionStart = selection,
            decodedWords = decodedWords.takeIf { text == value })
        if (preservesRaw) return edited
        val mode = when {
            !edited.isJapanese -> MnemonicDerivation.Legacy
            !isJapanese -> MnemonicDerivation.Bip39
            else -> derivation
        }
        return edited.copy(derivation = mode)
    }

    fun selectLegacy(enabled: Boolean): MnemonicImportDraft =
        if (isJapanese) {
            copy(derivation = if (enabled) MnemonicDerivation.Legacy else MnemonicDerivation.Bip39)
        } else this

    fun wordItems(): List<WordItem> = MnemonicInput.wordItems(text, lowercase = !preservesRaw)

    fun accountType(nonStandard: Boolean): AccountType.Mnemonic {
        val rawWords = decodedWords ?: wordItems().map { it.word }
        val preserve = preservesRaw || nonStandard
        val words = if (preserve) rawWords else rawWords.map { it.normalizeNFKD() }
        return AccountType.Mnemonic(words, if (preserve) passphrase else passphrase.normalizeNFKD(), derivation)
    }

    companion object {
        fun manual(text: String): MnemonicImportDraft = MnemonicImportDraft().editText(text, text.length)

        fun decoded(seed: SeedPhraseQrCrypto.DecryptedSeed): MnemonicImportDraft {
            val text = seed.words.joinToString(" ")
            val languages = Bip39LanguageDetector.detectExact(seed.words)
            return MnemonicImportDraft(
                text = text, cursorPosition = text.length, passphrase = seed.passphrase,
                passphraseEnabled = seed.passphrase.isNotEmpty(), height = seed.height?.toString().orEmpty(),
                isMoneroMnemonic = seed.words.size == 25 && seed.height != null,
                language = seed.language?.takeIf { it in languages } ?: languages.firstOrNull() ?: Language.English,
                derivation = seed.derivation, source = Source.DecodedQr, decodedWords = seed.words
            )
        }
    }
}

object MnemonicInput {
    private val rawTokens = Regex("\\S+")
    // Android ICU does not support (?U); spell out Unicode White_Space for both runtimes.
    private val japaneseTokens = Regex("[^\\p{Z}\\u0009-\\u000D\\u0085]+")

    fun isJapanese(text: String): Boolean {
        val words = canonicalWords(text)
        val japanese = WordList.wordList(Language.Japanese)
        return words.any { japanese.validWord(it, false) }
    }

    data class Analysis(
        val invalidItems: List<WordItem>, val invalidRanges: List<IntRange>, val suggestions: WordSuggestions?
    )

    fun analyze(items: List<WordItem>, cursor: Int, list: MnemonicWordList, normalize: Boolean): Analysis {
        fun word(item: WordItem) = if (normalize) item.word.normalizeNFKD() else item.word
        val invalid = items.filter { !list.validWord(word(it), false) }
        val current = items.find { it.range.contains(cursor - 1) }
        val highlighted = invalid.filter { it != current || !list.validWord(word(it), true) }
        val suggestions = current?.let { WordSuggestions(it, list.fetchSuggestions(word(it))) }
        return Analysis(invalid, highlighted.map { it.range }, suggestions)
    }

    fun canonicalWords(text: String): List<String> =
        japaneseTokens.findAll(text.normalizeNFKD()).map { it.value }.toList()

    fun wordItems(text: String, lowercase: Boolean): List<WordItem> {
        val regex = if (isJapanese(text)) japaneseTokens else rawTokens
        return regex.findAll(text).map {
            WordItem(if (lowercase) it.value.lowercase() else it.value, it.range)
        }.toList()
    }
}
