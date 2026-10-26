package cash.p.terminal.modules.contacts

import cash.p.terminal.modules.contacts.model.Contact

interface ContactsBackup {
    val contacts: List<Contact>
    val contactsJson: String

    fun parseContacts(json: String): List<Contact>

    fun restoreContacts(contacts: List<Contact>)
}

class ContactsSettingsBackup(private val contactsRepository: ContactsRepository) : ContactsBackup {
    override val contacts: List<Contact>
        get() = contactsRepository.contacts

    override val contactsJson: String
        get() = contactsRepository.asJsonString

    override fun parseContacts(json: String): List<Contact> = contactsRepository.parseFromJson(json)

    override fun restoreContacts(contacts: List<Contact>) {
        contactsRepository.restore(contacts)
    }
}
