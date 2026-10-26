package cash.p.terminal.core

import cash.p.terminal.resources.Res
import cash.p.terminal.resources.platform_not_supported_yet
import cash.p.terminal.strings.helpers.TranslatableString
import io.horizontalsystems.core.Translator
import org.jetbrains.compose.resources.StringResource

class UnsupportedException(override val message: String?) : Exception()
class UnsupportedAccountException : Exception()
class LocalizedException(val errorTextRes: StringResource, vararg val formatArgs: Any) : Exception()
class MoneroRescanException(override val message: String?) : Exception()
class ZcashRescanException(override val message: String?) : Exception()

/** The wallet's chain has no adapter on this platform yet. */
class NotSupportedOnPlatformException : Exception(Translator.getString(Res.string.platform_not_supported_yet))

fun LocalizedException.toResString(): TranslatableString =
    TranslatableString.ResString(errorTextRes, *formatArgs)

fun LocalizedException.toLocalizedString(): String = toResString().toString()
