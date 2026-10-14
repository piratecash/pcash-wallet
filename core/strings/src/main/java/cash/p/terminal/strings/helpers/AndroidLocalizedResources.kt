package cash.p.terminal.strings.helpers

import io.horizontalsystems.core.LocalizedResources
import java.util.concurrent.ConcurrentHashMap

class AndroidLocalizedResources : LocalizedResources {
    private val ids = ConcurrentHashMap<String, Int>()

    override fun getString(key: String): String =
        Translator.getString(resourceId(key, "string"))

    override fun getStringArrayItem(key: String, index: Int): String =
        Translator.getStringArrayItem(resourceId(key, "array"), index)

    // Compose resource keys match the Android resource names generated from the same XML.
    private fun resourceId(key: String, type: String): Int =
        ids.getOrPut("$type/$key") {
            val context = LibraryInitializer.getApplicationContext()
            context.resources.getIdentifier(key, type, context.packageName)
        }
}
