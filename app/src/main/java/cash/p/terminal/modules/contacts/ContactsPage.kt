package cash.p.terminal.modules.contacts

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import cash.p.terminal.R
import cash.p.terminal.core.App
import cash.p.terminal.core.authorizedDeleteContactsPasscodeAction
import cash.p.terminal.core.premiumAction
import cash.p.terminal.core.slideToDeleteContactsTerms
import cash.p.terminal.modules.contacts.model.Contact
import cash.p.terminal.modules.contacts.model.ContactAddress
import cash.p.terminal.modules.contacts.screen.AddressScreen
import cash.p.terminal.modules.contacts.screen.BlockchainSelectorScreen
import cash.p.terminal.modules.contacts.screen.ContactScreen
import cash.p.terminal.modules.contacts.screen.ContactsScreen
import cash.p.terminal.modules.contacts.screen.ContactsSettingsScreen
import cash.p.terminal.modules.contacts.viewmodel.AddressViewModel
import cash.p.terminal.modules.contacts.viewmodel.ContactViewModel
import cash.p.terminal.modules.contacts.viewmodel.ContactsViewModel
import cash.p.terminal.modules.pin.EditPinPage
import cash.p.terminal.modules.pin.PinType
import cash.p.terminal.modules.pin.SetPinPage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpFrom
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.navigation.viewModelStoreOwnerForPage
import kotlinx.parcelize.Parcelize

class ContactsPage(val input: Input) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val mode = input.mode
        if (mode is Mode.AddAddressToNewContact) {
            ContactContent(navigation, this, mode, contact = null, newAddress = mode.addAddress())
        } else {
            val newAddress = mode.addAddress()
            val viewModel = viewModel<ContactsViewModel>(factory = ContactsModule.ContactsViewModelFactory(mode))
            ContactsScreen(
                viewModel = viewModel,
                onNavigateToBack = navigation::navigateUpSafely,
                onNavigateToCreateContact = { navigation.slideFromRight(ContactPage(mode, null, newAddress)) },
                onNavigateToSettings = { navigation.slideFromRight(ContactsSettingsPage(mode)) },
                onNavigateToContact = { contact ->
                    navigation.slideFromRight(ContactPage(mode, contact, newAddress))
                }
            )
        }
    }

    @Parcelize
    data class Input(val mode: Mode) : Parcelable
}

class ContactPage(val mode: Mode, val contact: Contact?, val newAddress: ContactAddress?) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        ContactContent(navigation, this, mode, contact, newAddress)
    }
}

class ContactsSettingsPage(val mode: Mode) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        ContactsSettingsContent(navigation, mode)
    }
}

class ContactAddressPage(
    val contactUid: String?,
    val contactAddress: ContactAddress?,
    val definedAddresses: List<ContactAddress>?,
) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel: AddressViewModel = viewModel(
            factory = ContactsModule.AddressViewModelFactory(contactUid, contactAddress, definedAddresses)
        )
        AddressScreen(
            navigation = navigation,
            viewModel = viewModel,
            onNavigateToBlockchainSelector = { navigation.slideFromRight(ContactBlockchainSelectorPage()) },
            onDone = { address ->
                navigation.setResult(this, ContactAddressResult(addedAddress = address))
                navigation.navigateUpSafely()
            },
            onDelete = { address ->
                navigation.setResult(this, ContactAddressResult(deletedAddress = address))
                navigation.navigateUpSafely()
            },
            onNavigateToBack = navigation::navigateUpSafely
        )
    }
}

class ContactBlockchainSelectorPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel: AddressViewModel = viewModel(
            viewModelStoreOwner = navigation.viewModelStoreOwnerForPage(ContactAddressPage::class)
        )
        BlockchainSelectorScreen(
            blockchains = viewModel.uiState.availableBlockchains,
            selectedBlockchain = viewModel.uiState.blockchain,
            onSelectBlockchain = { blockchain ->
                viewModel.onEnterBlockchain(blockchain)
                navigation.navigateUpSafely()
            },
            onNavigateToBack = navigation::navigateUpSafely
        )
    }
}

@Parcelize
data class ContactAddressResult(
    val addedAddress: ContactAddress? = null,
    val deletedAddress: ContactAddress? = null,
) : Parcelable

@Composable
private fun ContactContent(
    navigation: HSNavigation,
    page: HSPage,
    mode: Mode,
    contact: Contact?,
    newAddress: ContactAddress?,
) {
    val viewModel = viewModel<ContactViewModel>(factory = ContactsModule.ContactViewModelFactory(contact, newAddress))
    ContactScreen(
        viewModel = viewModel,
        onNavigateToBack = {
            if (mode == Mode.Full) {
                navigation.navigateUpFrom(page)
            } else {
                navigation.removeLastUntil(ContactsPage::class, true)
            }
        },
        onNavigateToAddress = { address ->
            navigation.slideFromRightForResult<ContactAddressResult>(
                ContactAddressPage(
                    contactUid = viewModel.contact.uid,
                    contactAddress = address,
                    definedAddresses = viewModel.uiState.addressViewItems.map { it.contactAddress },
                )
            ) { result ->
                result.addedAddress?.let(viewModel::setAddress)
                result.deletedAddress?.let(viewModel::deleteAddress)
            }
        }
    )
}

private fun Mode.addAddress(): ContactAddress? = when (this) {
    is Mode.AddAddressToExistingContact ->
        App.marketKit.blockchain(blockchainType.uid)?.let { ContactAddress(it, address) }
    is Mode.AddAddressToNewContact ->
        App.marketKit.blockchain(blockchainType.uid)?.let { ContactAddress(it, address) }
    Mode.Full -> null
}

@Composable
private fun ContactsSettingsContent(navigation: HSNavigation, mode: Mode) {
    val viewModel = viewModel<ContactsViewModel>(factory = ContactsModule.ContactsViewModelFactory(mode))
    val uiState = viewModel.uiState
    ContactsSettingsScreen(
        deleteContactsPinEnabled = uiState.deleteContactsPinEnabled,
        hasContacts = viewModel.shouldShowRestoreWarning(),
        backupJson = viewModel.backupJson,
        backupFileName = viewModel.backupFileName,
        onRestore = viewModel::restore,
        onPrepareForBackup = viewModel::prepareForBackup,
        onManageDeleteContactsPasscode = {
            if (uiState.deleteContactsPinEnabled) {
                navigation.authorizedDeleteContactsPasscodeAction {
                    navigation.slideFromRight(
                        EditPinPage(
                            SetPinPage.Input(
                                descriptionResId = R.string.pin_set_for_delete_all_contacts,
                                pinType = PinType.DELETE_CONTACTS
                            )
                        )
                    )
                }
            } else {
                navigation.premiumAction {
                    navigation.slideToDeleteContactsTerms()
                }
            }
        },
        onDisableDeleteContactsPasscode = {
            navigation.authorizedDeleteContactsPasscodeAction {
                viewModel.disableDeleteContactsPin()
            }
        },
        onClose = navigation::navigateUpSafely
    )
}
