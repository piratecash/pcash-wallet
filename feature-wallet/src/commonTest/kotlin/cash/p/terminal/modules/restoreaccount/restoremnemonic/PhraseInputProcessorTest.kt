package cash.p.terminal.modules.restoreaccount.restoremnemonic

import cash.p.terminal.wallet.slip39.Slip39Wordlist
import io.horizontalsystems.hdwalletkit.Language
import io.horizontalsystems.hdwalletkit.MnemonicWordList
import io.horizontalsystems.hdwalletkit.WordList
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhraseInputProcessorTest {

    private val bip39 = WordList.wordListStrict(Language.English)
    private val slip39 = MnemonicWordList(Slip39Wordlist.words, false)

    @Test
    fun process_bip39InvalidWordBeforeCursor_highlightsIt() {
        val text = "abandon xyz about"

        val state = process(text, text.length, bip39)

        assertEquals(listOf("xyz"), state.invalidWordItems.map { it.word })
        assertEquals(listOf(8..10), state.invalidWordRanges)
    }

    @Test
    fun process_bip39WordBeingTypedIsValidPrefix_notHighlightedButSuggested() {
        val text = "abandon aba"

        val state = process(text, text.length, bip39)

        assertEquals(listOf("aba"), state.invalidWordItems.map { it.word })
        assertTrue(state.invalidWordRanges.isEmpty())
        assertEquals(listOf("abandon"), state.wordSuggestions?.options)
    }

    @Test
    fun process_slip39WordNotInBip39List_isValid() {
        val text = "academic acid acrobat "

        assertTrue(process(text, text.length, slip39).invalidWordItems.isEmpty())
        assertEquals(listOf("academic", "acrobat"), process(text, text.length, bip39).invalidWordItems.map { it.word })
    }

    @Test
    fun process_slip39TypedPrefix_suggestsSlip39Words() {
        val text = "ext"

        val state = process(text, text.length, slip39)

        assertTrue(state.invalidWordRanges.isEmpty())
        assertEquals(listOf("extend", "extra"), state.wordSuggestions?.options)
    }

    @Test
    fun wordItems_mixedCaseAndSpaces_lowercasesWithSourceRanges() {
        val items = PhraseInputProcessor.wordItems("  Extra  EXTEND")

        assertEquals(listOf("extra", "extend"), items.map { it.word })
        assertEquals(listOf(2..6, 9..14), items.map { it.range })
    }

    private fun process(text: String, cursor: Int, wordList: MnemonicWordList) =
        PhraseInputProcessor.process(PhraseInputProcessor.wordItems(text), cursor, wordList)
}
