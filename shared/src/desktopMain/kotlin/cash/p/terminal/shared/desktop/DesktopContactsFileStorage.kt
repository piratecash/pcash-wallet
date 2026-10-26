package cash.p.terminal.shared.desktop

import cash.p.terminal.modules.contacts.ContactsFileStorage
import cash.p.terminal.shared.desktop.vault.DesktopVaultFiles
import io.horizontalsystems.core.IEncryptionManager

/** Contacts are personal data, so the file is sealed with the vault session key and wiped with the vault. */
class DesktopContactsFileStorage(
    private val files: DesktopVaultFiles,
    private val encryption: IEncryptionManager,
) : ContactsFileStorage {

    override fun read(): String? =
        files.contactsFile.takeIf { it.exists() }?.readText()?.let(encryption::decrypt)

    override fun write(json: String) {
        val target = files.contactsFile
        target.parentFile?.mkdirs()
        writeFileAtomically(target.toPath(), encryption.encrypt(json).toByteArray(Charsets.UTF_8))
    }
}
