package io.horizontalsystems.core

import org.jetbrains.compose.resources.StringArrayResource
import org.jetbrains.compose.resources.StringResource
import java.math.BigInteger
import java.util.Locale

expect object Translator {
    fun getString(res: StringResource, vararg args: Any): String

    fun getStringArrayItem(array: StringArrayResource, index: Int): String
}

private val placeholder = Regex("%(?:(\\d+)\\$)?([sd%])")

/** Compose Resources substitute only positional `%1$s`; the shared strings also use Android's `%s`/`%d`. */
internal fun String.formatWithArgs(args: Array<out Any>): String {
    if (args.isEmpty()) return this
    var nextIndex = 0
    return placeholder.replace(this) { match ->
        if (match.groupValues[2] == "%") {
            "%"
        } else {
            val index = match.groups[1]?.value?.toInt()?.minus(1) ?: nextIndex++
            args[index].formatFor(match.groupValues[2])
        }
    }
}

/** `%d` on integers must localize digits (e.g. fa) like Android's `Context.getString` did. */
private fun Any.formatFor(conversion: String): String =
    if (conversion == "d" && isIntegral()) {
        String.format(Locale.getDefault(), "%d", this)
    } else {
        toString()
    }

private fun Any.isIntegral(): Boolean =
    when (this) {
        is Int, is Long, is Short, is Byte, is BigInteger -> true
        else -> false
    }
