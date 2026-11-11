package cash.p.terminal.shared.desktop.vault

import cash.p.terminal.core.storage.APP_DATABASE_NAME
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** The files the desktop vault owns; the public caches of the data directory are not among them. */
class DesktopVaultFiles(private val dataDirectory: File) {

    val vaultFile: File get() = File(dataDirectory, VAULT_FILE_NAME)

    /** Slot and shared databases; their number never changes, so the directory shows no level count. */
    val vaultDirectory: File get() = File(dataDirectory, VAULT_DIRECTORY_NAME)

    val kitsDirectory: File get() = File(dataDirectory, KITS_DIRECTORY_NAME)

    /** Under [kitsDirectory], so wiping the vault deletes it too. */
    val contactsFile: File get() = File(kitsDirectory, CONTACTS_FILE_NAME)

    val sharedDatabaseFile: File get() = File(vaultDirectory, SHARED_FILE_NAME)

    fun safeFile(slot: Int): File = File(vaultDirectory, "$SAFE_FILE_PREFIX.$slot")

    fun premiumFile(slot: Int): File = File(vaultDirectory, "$PREMIUM_FILE_PREFIX.$slot")

    fun accountStoreDirectory(storageId: String): File = File(kitsDirectory, opaqueName(storageId, ACCOUNT_DIRECTORY))

    fun accountStoreFile(storageId: String): File =
        File(accountStoreDirectory(storageId), opaqueName(storageId, ACCOUNT_DATABASE))

    /** The directory of one kit inside the directory of its account, so deleting the account deletes the kit's files. */
    fun kitDirectory(storageId: String, kitName: String): File =
        File(accountStoreDirectory(storageId), opaqueName(storageId, kitName))

    /** The slot file plus any temp copy an interrupted publish left behind — each one opens the vault. */
    fun slotFiles(): List<File> = listOf(vaultFile) + orphanSlotTemps()

    /** The root-level database of the earlier layout; it is plaintext, so a wipe must not leave it. */
    fun databaseFiles(): List<File> = DATABASE_SUFFIXES.map { File(dataDirectory, APP_DATABASE_NAME + it) }

    /** A directory we cannot list may hide a usable slot copy, so "no orphans" must not be assumed. */
    private fun orphanSlotTemps(): List<File> {
        val entries = dataDirectory.listFiles() ?: throw IOException("Cannot list $dataDirectory")
        return entries.filter { it.name.startsWith("$VAULT_FILE_NAME.") && it.name.endsWith(TEMP_SUFFIX) }
    }

    fun opaqueName(secret: String, name: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest("$secret$name".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(OPAQUE_NAME_LENGTH)

    private companion object {
        const val VAULT_FILE_NAME = "vault.pcv"
        const val VAULT_DIRECTORY_NAME = "vault"
        const val KITS_DIRECTORY_NAME = "kits"
        const val CONTACTS_FILE_NAME = "contacts.enc"
        const val TEMP_SUFFIX = ".tmp"
        const val SAFE_FILE_PREFIX = "app"
        const val PREMIUM_FILE_PREFIX = "prem"
        const val SHARED_FILE_NAME = "shared"
        const val ACCOUNT_DIRECTORY = "account"
        const val ACCOUNT_DATABASE = "account.db"
        const val OPAQUE_NAME_LENGTH = 32
        val DATABASE_SUFFIXES = listOf("", "-wal", "-shm", "-journal")
    }
}
