package io.horizontalsystems.core

import org.jetbrains.compose.resources.StringArrayResource
import org.jetbrains.compose.resources.StringResource
import org.koin.core.context.GlobalContext

actual object Translator {
    actual fun getString(res: StringResource, vararg args: Any): String =
        localizedResources()?.getString(res.key)?.formatWithArgs(args) ?: res.key

    actual fun getStringArrayItem(array: StringArrayResource, index: Int): String =
        localizedResources()?.getStringArrayItem(array.key, index) ?: array.key

    // Previews and host tests run without Koin: fall back to the key instead of blocking or throwing.
    private fun localizedResources(): LocalizedResources? =
        GlobalContext.getOrNull()?.getOrNull<LocalizedResources>()
}
