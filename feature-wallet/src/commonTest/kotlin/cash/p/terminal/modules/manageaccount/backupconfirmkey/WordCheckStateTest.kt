package cash.p.terminal.modules.manageaccount.backupconfirmkey

import cash.p.terminal.core.managers.RandomProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WordCheckStateTest {

    private val shareWords = List(20) { "word$it" }

    @Test
    fun create_twentyWordShare_asksThreeWords() {
        assertEquals(3, WordCheckState.create(shareWords, RandomProvider()).questions.size)
    }

    @Test
    fun create_thirtyThreeWordShare_asksFourWords() {
        assertEquals(4, WordCheckState.create(List(33) { "word$it" }, RandomProvider()).questions.size)
    }

    @Test
    fun create_twelveWordPhrase_asksTwoWords() {
        assertEquals(2, WordCheckState.create(List(12) { "word$it" }, RandomProvider()).questions.size)
    }

    @Test
    fun create_everyQuestion_offersRequestedWordOnceAmongSixDistinctOptions() {
        WordCheckState.create(shareWords, RandomProvider()).questions.forEach { question ->
            assertEquals(shareWords[question.position - 1], question.word)
            assertEquals(6, question.options.distinct().size)
            assertEquals(1, question.options.count { it == question.word })
        }
    }

    @Test
    fun create_phraseWithRepeatedWords_offersOnlyDistinctOptions() {
        val phrase = List(11) { "abandon" } + "about"

        WordCheckState.create(phrase, RandomProvider()).questions.forEach { question ->
            assertEquals(listOf("abandon", "about").sorted(), question.options.sorted())
        }
    }

    @Test
    fun select_wrongWord_marksOnlyThatOptionAndKeepsProgress() {
        val afterFirst = WordCheckState.create(shareWords, RandomProvider()).answerCorrectly()
        val wrongIndex = afterFirst.wrongOptionIndex()

        val state = afterFirst.select(wrongIndex)

        assertEquals(1, state.answered)
        assertEquals(wrongIndex, state.wrongOptionIndex)
        assertEquals(afterFirst.current, state.current)
    }

    @Test
    fun select_correctWordAfterMistake_advancesAndClearsMark() {
        val initial = WordCheckState.create(shareWords, RandomProvider())
        val state = initial.select(initial.wrongOptionIndex()).answerCorrectly()

        assertEquals(1, state.answered)
        assertNull(state.wrongOptionIndex)
        assertEquals(2, state.remaining)
    }

    @Test
    fun select_everyRequestedWord_completes() {
        var state = WordCheckState.create(shareWords, RandomProvider())
        repeat(state.questions.size) {
            assertFalse(state.isComplete)
            state = state.answerCorrectly()
        }

        assertTrue(state.isComplete)
        assertNull(state.current)
    }

    private fun WordCheckState.answerCorrectly(): WordCheckState {
        val question = requireNotNull(current)
        return select(question.options.indexOf(question.word))
    }

    private fun WordCheckState.wrongOptionIndex(): Int {
        val question = requireNotNull(current)
        return question.options.indexOfFirst { it != question.word }
    }
}
