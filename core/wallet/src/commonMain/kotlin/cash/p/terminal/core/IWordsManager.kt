package cash.p.terminal.core

import io.horizontalsystems.hdwalletkit.Language

interface IWordsManager {
    fun validateChecksum(words: List<String>)
    fun validateChecksumStrict(words: List<String>)
    fun isWordValid(word: String): Boolean
    fun isWordPartiallyValid(word: String): Boolean
    fun generateWords(count: Int, language: Language): List<String>
}
