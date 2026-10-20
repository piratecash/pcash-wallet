package cash.p.terminal.shared.desktop.vault

import cash.p.terminal.core.storage.APP_DATABASE_NAME
import java.io.File
import java.io.IOException

/** The files the desktop vault owns; the public caches of the data directory are not among them. */
class DesktopVaultFiles(private val dataDirectory: File) {

    val vaultFile: File get() = File(dataDirectory, VAULT_FILE_NAME)

    val databaseFile: File get() = File(dataDirectory, APP_DATABASE_NAME)

    val kitsDirectory: File get() = File(dataDirectory, KITS_DIRECTORY_NAME)

    /** The slot file plus any temp copy an interrupted publish left behind — each one opens the vault. */
    fun slotFiles(): List<File> = listOf(vaultFile) + orphanSlotTemps()

    fun databaseFiles(): List<File> = DATABASE_SUFFIXES.map { File(dataDirectory, APP_DATABASE_NAME + it) }

    /** A directory we cannot list may hide a usable slot copy, so "no orphans" must not be assumed. */
    private fun orphanSlotTemps(): List<File> {
        val entries = dataDirectory.listFiles() ?: throw IOException("Cannot list $dataDirectory")
        return entries.filter { it.name.startsWith("$VAULT_FILE_NAME.") && it.name.endsWith(TEMP_SUFFIX) }
    }

    private companion object {
        const val VAULT_FILE_NAME = "vault.pcv"
        const val KITS_DIRECTORY_NAME = "kits"
        const val TEMP_SUFFIX = ".tmp"
        val DATABASE_SUFFIXES = listOf("", "-wal", "-shm", "-journal")
    }
}
