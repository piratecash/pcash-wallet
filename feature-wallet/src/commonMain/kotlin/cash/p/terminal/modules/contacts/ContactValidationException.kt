package cash.p.terminal.modules.contacts

import cash.p.terminal.modules.contacts.model.Contact
import cash.p.terminal.resources.Contacts_Error_DefinedAddress
import cash.p.terminal.resources.Contacts_Error_DefinedName
import cash.p.terminal.resources.Res
import io.horizontalsystems.core.Translator

sealed class ContactValidationException(override val message: String?) : Throwable() {
    object DuplicateContactName :
        ContactValidationException(Translator.getString(Res.string.Contacts_Error_DefinedName))

    class DuplicateAddress(val contact: Contact) :
        ContactValidationException(Translator.getString(Res.string.Contacts_Error_DefinedAddress, contact.name))
}
