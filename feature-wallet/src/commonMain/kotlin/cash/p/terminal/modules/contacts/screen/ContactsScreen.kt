package cash.p.terminal.modules.contacts.screen

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import cash.p.terminal.core.Caution
import cash.p.terminal.modules.contacts.model.Contact
import cash.p.terminal.modules.contacts.viewmodel.ContactsViewModel
import cash.p.terminal.resources.Alert_TitleWarning
import cash.p.terminal.resources.Button_Cancel
import cash.p.terminal.resources.Contacts
import cash.p.terminal.resources.Contacts_AddAddress_Replace
import cash.p.terminal.resources.Contacts_AddNewContact
import cash.p.terminal.resources.Contacts_AddressesCount
import cash.p.terminal.resources.Contacts_NewContact
import cash.p.terminal.resources.Contacts_NoContacts
import cash.p.terminal.resources.EmptyResults
import cash.p.terminal.resources.Market_Search_Hint
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Settings_Title
import cash.p.terminal.resources.ic_arrow_right
import cash.p.terminal.resources.ic_manage_2
import cash.p.terminal.resources.ic_not_found
import cash.p.terminal.resources.icon_user_plus
import cash.p.terminal.resources.icon_warning_2_20
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui.compose.components.ListEmptyView
import cash.p.terminal.ui.compose.components.ScreenMessageWithAction
import cash.p.terminal.ui.compose.components.SearchBar
import cash.p.terminal.ui_compose.components.AppModalBottomSheetLayout
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

enum class ContactsScreenBottomSheetType {
    ReplaceAddressConfirmation
}

@OptIn(ExperimentalMaterialApi::class, ExperimentalAnimationApi::class)
@Composable
fun ContactsScreen(
    viewModel: ContactsViewModel,
    onNavigateToBack: () -> Unit,
    onNavigateToCreateContact: () -> Unit,
    onNavigateToContact: (Contact) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val uiState = viewModel.uiState

    var bottomSheetType: ContactsScreenBottomSheetType? by remember { mutableStateOf(null) }
    val bottomSheetState = rememberModalBottomSheetState(ModalBottomSheetValue.Hidden)
    val coroutineScope = rememberCoroutineScope()
    var selectedContact by remember { mutableStateOf<Contact?>(null) }

    AppModalBottomSheetLayout(
        sheetState = bottomSheetState,
        sheetContent = {
            when (bottomSheetType) {
                null -> {
                    Spacer(modifier = Modifier.height(1.dp))
                }

                ContactsScreenBottomSheetType.ReplaceAddressConfirmation -> {
                    val warningMessage =
                        selectedContact?.let { viewModel.replaceWarningMessage(it)?.getString() }
                    ConfirmationBottomSheet(
                        title = stringResource(Res.string.Alert_TitleWarning),
                        text = warningMessage ?: "",
                        iconPainter = painterResource(Res.drawable.icon_warning_2_20),
                        iconTint = ColorFilter.tint(ComposeAppTheme.colors.statusWarning),
                        confirmText = stringResource(Res.string.Contacts_AddAddress_Replace),
                        cautionType = Caution.Type.Warning,
                        cancelText = stringResource(Res.string.Button_Cancel),
                        onConfirm = {
                            selectedContact?.let {
                                coroutineScope.launch {
                                    bottomSheetState.hide()
                                    onNavigateToContact(it)
                                }
                            }
                        },
                        onClose = {
                            coroutineScope.launch { bottomSheetState.hide() }
                        }
                    )
                }

            }

        }
    ) {
        Scaffold(
            containerColor = ComposeAppTheme.colors.backgroundBase,
            topBar = {
                SearchBar(
                    title = stringResource(Res.string.Contacts),
                    searchHintText = stringResource(Res.string.Market_Search_Hint),
                    menuItems = buildList {
                        if (uiState.showAddContact) {
                            add(
                                MenuItem(
                                    title = TranslatableString.ResString(Res.string.Contacts_NewContact),
                                    icon = Res.drawable.icon_user_plus,
                                    tint = ComposeAppTheme.colors.brandDefault,
                                    onClick = onNavigateToCreateContact
                                )
                            )
                        }
                        if (uiState.showSettings) {
                            add(
                                MenuItem(
                                    title = TranslatableString.ResString(Res.string.Settings_Title),
                                    icon = Res.drawable.ic_manage_2,
                                    tint = ComposeAppTheme.colors.brandDefault,
                                    enabled = true,
                                    onClick = onNavigateToSettings
                                )
                            )
                        }
                    },
                    onClose = onNavigateToBack,
                    onSearchTextChanged = { text ->
                        viewModel.onEnterQuery(text)
                    }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(paddingValues)
            ) {
                if (uiState.contacts.isNotEmpty()) {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        VSpacer(12.dp)
                        CellUniversalLawrenceSection(uiState.contacts) { contact ->
                            Contact(contact) {
                                if (viewModel.shouldShowReplaceWarning(contact)) {
                                    coroutineScope.launch {
                                        bottomSheetType =
                                            ContactsScreenBottomSheetType.ReplaceAddressConfirmation
                                        selectedContact = contact
                                        bottomSheetState.show()
                                    }
                                } else {
                                    onNavigateToContact(contact)
                                }
                            }
                        }
                        VSpacer(32.dp)
                    }
                } else {
                    if (uiState.searchMode) {
                        ListEmptyView(
                            text = stringResource(Res.string.EmptyResults),
                            icon = Res.drawable.ic_not_found
                        )
                    } else {
                        ScreenMessageWithAction(
                            text = stringResource(Res.string.Contacts_NoContacts),
                            icon = Res.drawable.icon_user_plus
                        ) {
                            ButtonPrimaryYellow(
                                modifier = Modifier
                                    .padding(horizontal = 48.dp)
                                    .fillMaxWidth(),
                                title = stringResource(Res.string.Contacts_AddNewContact),
                                onClick = onNavigateToCreateContact
                            )
                        }
                    }
                }

            }
        }
    }
}

@Composable
fun Contact(
    contact: Contact,
    onClick: () -> Unit
) {
    RowUniversal(
        modifier = Modifier.padding(horizontal = 16.dp),
        onClick = onClick
    ) {
        Column(modifier = Modifier.weight(1f)) {
            body_leah(
                text = contact.name,
                maxLines = 1
            )
            subhead2_grey(
                text = stringResource(Res.string.Contacts_AddressesCount).format(contact.addresses.size)
            )
        }
        Image(
            modifier = Modifier.size(20.dp),
            painter = painterResource(Res.drawable.ic_arrow_right),
            contentDescription = null,
        )
    }
}
