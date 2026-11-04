package cash.p.terminal.modules.contacts

import androidx.compose.runtime.Composable
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
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpFrom
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.navigation.rememberResultLauncher
import cash.p.terminal.navigation.slideFromRight
import cash.p.terminal.navigation.viewModelStoreOwnerForPage
import cash.p.terminal.wallet.MarketKitWrapper
import io.horizontalsystems.core.common.CommonParcelable
import io.horizontalsystems.core.common.CommonParcelize
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class ContactsPage(val input: Input) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val mode = input.mode
        val newAddress = mode.addAddress(koinInject())
        if (mode is Mode.AddAddressToNewContact) {
            ContactContent(navigation, this, mode, contact = null, newAddress = newAddress)
        } else {
            val viewModel = koinViewModel<ContactsViewModel> { parametersOf(mode) }
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

    @CommonParcelize
    data class Input(val mode: Mode) : CommonParcelable
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
        val viewModel = koinViewModel<AddressViewModel> {
            parametersOf(contactUid, contactAddress, definedAddresses)
        }
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
        val viewModel = koinViewModel<AddressViewModel>(
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

@CommonParcelize
data class ContactAddressResult(
    val addedAddress: ContactAddress? = null,
    val deletedAddress: ContactAddress? = null,
) : CommonParcelable

@Composable
private fun ContactContent(
    navigation: HSNavigation,
    page: HSPage,
    mode: Mode,
    contact: Contact?,
    newAddress: ContactAddress?,
) {
    val viewModel = koinViewModel<ContactViewModel> { parametersOf(contact, newAddress) }
    val addressEditor = navigation.rememberResultLauncher<ContactAddressResult> { result ->
        result.addedAddress?.let(viewModel::setAddress)
        result.deletedAddress?.let(viewModel::deleteAddress)
    }
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
            addressEditor.slideFromRight(
                ContactAddressPage(
                    contactUid = viewModel.contact.uid,
                    contactAddress = address,
                    definedAddresses = viewModel.uiState.addressViewItems.map { it.contactAddress },
                )
            )
        }
    )
}

private fun Mode.addAddress(marketKit: MarketKitWrapper): ContactAddress? = when (this) {
    is Mode.AddAddressToExistingContact ->
        marketKit.blockchain(blockchainType.uid)?.let { ContactAddress(it, address) }
    is Mode.AddAddressToNewContact ->
        marketKit.blockchain(blockchainType.uid)?.let { ContactAddress(it, address) }
    Mode.Full -> null
}

@Composable
private fun ContactsSettingsContent(navigation: HSNavigation, mode: Mode) {
    val viewModel = koinViewModel<ContactsViewModel> { parametersOf(mode) }
    val uiState = viewModel.uiState
    ContactsSettingsScreen(
        navigation = navigation,
        hasContacts = uiState.hasContacts,
        backupJson = uiState.backupJson,
        backupFileName = viewModel.backupFileName,
        onRestore = viewModel::restore,
        onClose = navigation::navigateUpSafely
    )
}
