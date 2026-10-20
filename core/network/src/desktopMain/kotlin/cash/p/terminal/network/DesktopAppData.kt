package cash.p.terminal.network

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

/** The per-OS private application data directory, created if needed. */
fun desktopAppDataDirectory(): File {
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
    return dataDirectory
}

/** Resolves [name] inside the per-OS private application data directory, creating it if needed. */
fun desktopAppDataFile(name: String): File = File(desktopAppDataDirectory(), name)

/** Same directory, restricted to the current user — directories of earlier installs included. */
fun secureDesktopAppDataDirectory(): File = desktopAppDataDirectory().also(::restrictToOwner)

internal fun restrictToOwner(directory: File) {
    try {
        Files.setPosixFilePermissions(
            directory.toPath(),
            setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE),
        )
    } catch (e: UnsupportedOperationException) {
        // Windows has no POSIX permissions; the directory inherits the user's ACL.
    }
}
