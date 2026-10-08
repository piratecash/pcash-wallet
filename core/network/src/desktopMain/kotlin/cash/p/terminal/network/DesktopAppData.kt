package cash.p.terminal.network

import java.io.File

/** Resolves [name] inside the per-OS private application data directory, creating it if needed. */
fun desktopAppDataFile(name: String): File {
    val userHome = checkNotNull(System.getProperty("user.home"))
    val osName = System.getProperty("os.name").orEmpty()
    val dataDirectory = when {
        osName.startsWith("Mac", ignoreCase = true) ->
            File(userHome, "Library/Application Support/p.cash")
        osName.startsWith("Windows", ignoreCase = true) ->
            File(System.getenv("APPDATA") ?: userHome, "p.cash")
        else ->
            File(System.getenv("XDG_DATA_HOME") ?: "$userHome/.local/share", "p.cash")
    }
    check(dataDirectory.isDirectory || dataDirectory.mkdirs()) {
        "Unable to create application data directory: $dataDirectory"
    }
    return File(dataDirectory, name)
}
