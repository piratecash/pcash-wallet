package cash.p.terminal.modules.contacts

import cash.p.terminal.resources.Contacts_AddAddress_ExistingContact
import cash.p.terminal.resources.Contacts_AddAddress_NewContact
import cash.p.terminal.resources.Res
import org.jetbrains.compose.resources.StringResource

object ContactsModule {

    enum class AddAddressAction(val title: StringResource) {
        AddToNewContact(Res.string.Contacts_AddAddress_NewContact),
        AddToExistingContact(Res.string.Contacts_AddAddress_ExistingContact)
    }
}
