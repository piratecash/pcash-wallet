package cash.p.terminal.core.usecase

import cash.p.terminal.core.managers.WordsManager
import cash.p.terminal.core.utils.MoneroWalletSeedConverter

class ValidateMoneroMnemonicUseCase(
    private val commonWordsManager: WordsManager
) {
    operator fun invoke(mnemonicWords: List<String>, isMonero: Boolean) {
        if (isMonero) {
            validateMoneroChecksum(mnemonicWords)
        } else {
            commonWordsManager.validateChecksumStrict(mnemonicWords)
        }
    }

    private fun validateMoneroChecksum(mnemonicWords: List<String>) {
        if (mnemonicWords.size != 25) {
            throw IllegalArgumentException("Monero mnemonic must be 25 words long")
        }

        val checksumWord = mnemonicWords[24]
        val expectedChecksumWord = MoneroWalletSeedConverter.checksumWord(mnemonicWords.take(24))

        if (expectedChecksumWord != checksumWord) {
            throw IllegalArgumentException(
                "Invalid Monero checksum: expected \"$expectedChecksumWord\" at position 25, got \"$checksumWord\""
            )
        }
    }
}