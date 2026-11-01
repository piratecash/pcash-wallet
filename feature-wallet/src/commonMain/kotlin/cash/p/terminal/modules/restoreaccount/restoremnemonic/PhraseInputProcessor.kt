package cash.p.terminal.modules.restoreaccount.restoremnemonic

import cash.p.terminal.core.utils.MNEMONIC_WORD_REGEX
import cash.p.terminal.modules.restoreaccount.restoremnemonic.RestoreMnemonicModule.WordItem
import cash.p.terminal.modules.restoreaccount.restoremnemonic.RestoreMnemonicModule.WordSuggestions
import cash.p.terminal.wallet.normalizeNFKD
import io.horizontalsystems.hdwalletkit.MnemonicWordList

data class PhraseInputState(
    val wordItems: List<WordItem> = emptyList(),
    val invalidWordItems: List<WordItem> = emptyList(),
    /** Invalid words to highlight: the word being typed is spared while it is still a valid prefix. */
    val invalidWordRanges: List<IntRange> = emptyList(),
    val wordSuggestions: WordSuggestions? = null,
)

object PhraseInputProcessor {

    fun wordItems(text: String): List<WordItem> =
        MNEMONIC_WORD_REGEX.findAll(text.lowercase())
            .map { WordItem(it.value, it.range) }
            .toList()

    fun process(wordItems: List<WordItem>, cursorPosition: Int, wordList: MnemonicWordList): PhraseInputState {
        val invalidWordItems = wordItems.filter { !wordList.validWord(it.word.normalizeNFKD(), false) }
        val wordItemWithCursor = wordItems.find { it.range.contains(cursorPosition - 1) }
        val typedPrefix = wordItemWithCursor?.takeIf { wordList.validWord(it.word.normalizeNFKD(), true) }

        return PhraseInputState(
            wordItems = wordItems,
            invalidWordItems = invalidWordItems,
            invalidWordRanges = invalidWordItems.filter { it != typedPrefix }.map { it.range },
            wordSuggestions = wordItemWithCursor?.let {
                WordSuggestions(it, wordList.fetchSuggestions(it.word.normalizeNFKD()))
            },
        )
    }
}
