package cash.p.terminal.core

import cash.p.terminal.R

class NotEnoughData() : Exception()

class AdapterErrorWrongParameters(override val message: String) : Exception()
class NoFeeSendTransactionError : Exception()
class FailedTransaction(errorMessage: String?) : RuntimeException(errorMessage) {
    override fun toString() = message ?: "Transaction failed."
}

class NoDataException() : Exception() {
    override fun getLocalizedMessage(): String {
        return cash.p.terminal.strings.helpers.Translator.getString(R.string.CoinPage_NoData)
    }
}
