package cash.p.terminal.modules.blockchainstatus

private enum class AbiFamily { ARM, X86 }

private class LoadedAbi(val name: String, val family: AbiFamily)

private val LOADED_ABIS = mapOf(
    "arm64" to LoadedAbi("arm64-v8a", AbiFamily.ARM),
    "arm" to LoadedAbi("armeabi-v7a", AbiFamily.ARM),
    "x86_64" to LoadedAbi("x86_64", AbiFamily.X86),
    "x86" to LoadedAbi("x86", AbiFamily.X86),
)

private val SUPPORTED_ABI_FAMILIES = mapOf(
    "arm64-v8a" to AbiFamily.ARM,
    "armeabi-v7a" to AbiFamily.ARM,
    "x86_64" to AbiFamily.X86,
    "x86" to AbiFamily.X86,
)

/** ABI of the native libraries the app was installed with; flags an ARM build running under x86 translation. */
internal fun nativeAbiLabel(nativeLibraryDir: String, supportedAbis: List<String>): String {
    val suffix = nativeLibraryDir.trimEnd('/').substringAfterLast('/')
    val loaded = LOADED_ABIS[suffix] ?: return suffix
    val preferred = supportedAbis.firstOrNull() ?: return loaded.name
    val preferredFamily = SUPPORTED_ABI_FAMILIES[preferred]
    return if (preferredFamily != null && preferredFamily != loaded.family) {
        "${loaded.name} (translated on $preferred)"
    } else {
        loaded.name
    }
}
