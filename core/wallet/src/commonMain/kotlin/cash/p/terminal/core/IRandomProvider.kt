package cash.p.terminal.core

interface IRandomProvider {
    fun getRandomNumbers(count: Int, maxIndex: Int): List<Int>
}
