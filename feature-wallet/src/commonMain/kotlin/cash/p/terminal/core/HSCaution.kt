package cash.p.terminal.core

import androidx.compose.runtime.Composable
import cash.p.terminal.strings.helpers.TranslatableString

open class HSCaution(
    val s: TranslatableString,
    val type: Type = Type.Error,
    val description: TranslatableString? = null
) {
    @Composable
    fun getString() = s.getString()

    @Composable
    fun getDescription() = description?.getString()

    enum class Type {
        Error, Warning
    }

    fun isError() = type == Type.Error
    fun isWarning() = type == Type.Warning
}
