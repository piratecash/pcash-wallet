package io.horizontalsystems.core

import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringArrayResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString as readString
import org.jetbrains.compose.resources.getStringArray as readStringArray

actual object Translator {
    // Compose Resources only expose suspend readers; each string is cached after its first short asset read.
    actual fun getString(res: StringResource, vararg args: Any): String =
        runBlocking { readString(res) }.formatWithArgs(args)

    actual fun getStringArrayItem(array: StringArrayResource, index: Int): String =
        runBlocking { readStringArray(array)[index] }
}
