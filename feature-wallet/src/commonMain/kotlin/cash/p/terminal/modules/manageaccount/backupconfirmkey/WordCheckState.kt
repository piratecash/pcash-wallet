package cash.p.terminal.modules.manageaccount.backupconfirmkey

import cash.p.terminal.core.IRandomProvider

/** A requested word: its 1-based [position] in the phrase and the [options] it is picked from. */
data class WordQuestion(val position: Int, val word: String, val options: List<String>)

/** Asks [questions] one by one; a wrong pick marks only that option and keeps the progress. */
data class WordCheckState(
    val questions: List<WordQuestion>,
    val answered: Int = 0,
    val wrongOptionIndex: Int? = null,
) {
    val current: WordQuestion? get() = questions.getOrNull(answered)
    val isComplete: Boolean get() = answered == questions.size
    val remaining: Int get() = questions.size - answered

    fun select(optionIndex: Int): WordCheckState {
        val question = current ?: return this
        return if (question.options[optionIndex] == question.word) {
            copy(answered = answered + 1, wrongOptionIndex = null)
        } else {
            copy(wrongOptionIndex = optionIndex)
        }
    }

    companion object {
        private const val OPTION_COUNT = 6

        fun create(words: List<String>, randomProvider: IRandomProvider): WordCheckState {
            val distinctWords = words.distinct()
            val positions = randomProvider.getRandomNumbers(minOf(wordsToGuess(words.size), words.size), words.size)
            return WordCheckState(positions.map { question(it, words[it], distinctWords, randomProvider) })
        }

        private fun question(
            index: Int,
            word: String,
            distinctWords: List<String>,
            randomProvider: IRandomProvider
        ): WordQuestion {
            val others = distinctWords - word
            val options = randomProvider.getRandomNumbers(minOf(OPTION_COUNT - 1, others.size), others.size)
                .map(others::get)
                .toMutableList()
            options.add(randomProvider.getRandomNumbers(1, options.size + 1).single(), word)
            return WordQuestion(position = index + 1, word = word, options = options)
        }

        private fun wordsToGuess(wordCount: Int): Int = when (wordCount) {
            15, 18, 20, 21 -> 3
            24, 25, 33 -> 4
            else -> 2
        }
    }
}
