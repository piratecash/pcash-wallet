package cash.p.terminal.modules.coin.indicators

import cash.p.terminal.resources.Error_NotInteger
import cash.p.terminal.resources.Error_OutOfRange
import cash.p.terminal.resources.Res
import io.horizontalsystems.core.Translator

class NotIntegerException : Exception() {
    override fun getLocalizedMessage(): String {
        return Translator.getString(Res.string.Error_NotInteger)
    }
}

class OutOfRangeException(val lower: Int, val upper: Int) : Exception() {
    override fun getLocalizedMessage(): String {
        return Translator.getString(Res.string.Error_OutOfRange, lower, upper)
    }
}
