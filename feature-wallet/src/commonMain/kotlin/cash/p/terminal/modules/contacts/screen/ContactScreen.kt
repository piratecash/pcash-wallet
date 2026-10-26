package cash.p.terminal.modules.contacts.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import cash.p.terminal.core.Caution
import cash.p.terminal.modules.contacts.model.ContactAddress
import cash.p.terminal.modules.contacts.viewmodel.ContactViewModel
import cash.p.terminal.modules.contacts.viewmodel.ContactViewModel.AddressViewItem
import cash.p.terminal.resources.Alert_TitleWarning
import cash.p.terminal.resources.Button_Cancel
import cash.p.terminal.resources.Button_Delete
import cash.p.terminal.resources.Button_Save
import cash.p.terminal.resources.Contacts_AddAddress
import cash.p.terminal.resources.Contacts_DeleteContact
import cash.p.terminal.resources.Contacts_DeleteContact_Warning
import cash.p.terminal.resources.Contacts_DiscardChanges
import cash.p.terminal.resources.Contacts_DiscardChanges_Warning
import cash.p.terminal.resources.Contacts_KeepEditing
import cash.p.terminal.resources.Contacts_NameHint
import cash.p.terminal.resources.Hud_Text_Done
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_delete_20
import cash.p.terminal.resources.ic_edit_20
import cash.p.terminal.resources.ic_platform_placeholder_32
import cash.p.terminal.resources.ic_plus
import cash.p.terminal.resources.icon_warning_2_20
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui.compose.components.FormsInput
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.AppModalBottomSheetLayout
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.SnackbarDuration
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_brand
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.body_lucian
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import cash.p.terminal.ui_compose.entities.DataState
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.chartview.rememberAsyncImagePainterWithFallback
import io.horizontalsystems.core.imageUrl
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

enum class ContactScreenBottomSheetType {
    DeleteConfirmation, DiscardChangesConfirmation
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ContactScreen(
    viewModel: ContactViewModel,
    onNavigateToBack: () -> Unit,
    onNavigateToAddress: (ContactAddress?) -> Unit
) {
    val uiState = viewModel.uiState
    val hudMessenger = rememberHudMessenger()
    val done = stringResource(Res.string.Hud_Text_Done)

    var bottomSheetType: ContactScreenBottomSheetType? by remember { mutableStateOf(null) }
    val modalBottomSheetState = rememberModalBottomSheetState(ModalBottomSheetValue.Hidden)
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(uiState.closeWithSuccess) {
        if (uiState.closeWithSuccess) {
            focusManager.clearFocus(true)

            hudMessenger.showSuccess(done, SnackbarDuration.SHORT)

            onNavigateToBack()
        }
    }

    AppModalBottomSheetLayout(
        sheetState = modalBottomSheetState,
        sheetContent = {
            when (bottomSheetType) {
                null -> {
                    Spacer(modifier = Modifier.height(1.dp))
                }

                ContactScreenBottomSheetType.DeleteConfirmation -> {
                    ConfirmationBottomSheet(
                        title = stringResource(Res.string.Contacts_DeleteContact),
                        text = stringResource(Res.string.Contacts_DeleteContact_Warning),
                        iconPainter = painterResource(Res.drawable.ic_delete_20),
                        iconTint = ColorFilter.tint(ComposeAppTheme.colors.statusError),
                        confirmText = stringResource(Res.string.Button_Delete),
                        cautionType = Caution.Type.Error,
                        cancelText = stringResource(Res.string.Button_Cancel),
                        onConfirm = viewModel::onDelete,
                        onClose = { coroutineScope.launch { modalBottomSheetState.hide() } }
                    )
                }

                ContactScreenBottomSheetType.DiscardChangesConfirmation -> {
                    ConfirmationBottomSheet(
                        title = stringResource(Res.string.Alert_TitleWarning),
                        text = stringResource(Res.string.Contacts_DiscardChanges_Warning),
                        iconPainter = painterResource(Res.drawable.icon_warning_2_20),
                        iconTint = ColorFilter.tint(ComposeAppTheme.colors.statusWarning),
                        confirmText = stringResource(Res.string.Contacts_DiscardChanges),
                        cautionType = Caution.Type.Error,
                        cancelText = stringResource(Res.string.Contacts_KeepEditing),
                        onConfirm = onNavigateToBack,
                        onClose = { coroutineScope.launch { modalBottomSheetState.hide() } }
                    )
                }
            }
        },
    ) {
        val confirmNavigateToBack: () -> Unit = {
            if (uiState.confirmBack) {
                bottomSheetType = ContactScreenBottomSheetType.DiscardChangesConfirmation
                coroutineScope.launch {
                    focusManager.clearFocus(true)
                    modalBottomSheetState.show()
                }
            } else {
                focusManager.clearFocus(true)
                onNavigateToBack()
            }
        }

        NavigationBackHandler(
            state = rememberNavigationEventState(NavigationEventInfo.None),
            onBackCompleted = {
                if (modalBottomSheetState.isVisible) {
                    coroutineScope.launch { modalBottomSheetState.hide() }
                } else {
                    confirmNavigateToBack()
                }
            },
        )

        Scaffold(
            containerColor = ComposeAppTheme.colors.backgroundBase,
            topBar = {
                AppBar(
                    title = uiState.headerTitle.getString(),
                    navigationIcon = {
                        HsBackButton {
                            confirmNavigateToBack()
                        }
                    },
                    menuItems = listOf(
                        MenuItem(
                            title = TranslatableString.ResString(Res.string.Button_Save),
                            enabled = uiState.saveEnabled,
                            onClick = viewModel::onSave
                        )
                    )
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
            ) {
                VSpacer(12.dp)
                FormsInput(
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .padding(horizontal = 16.dp)
                        .onGloballyPositioned {
                            if (uiState.focusOnContactName) {
                                focusRequester.requestFocus()
                            }
                        },
                    initial = viewModel.contact.name,
                    pasteEnabled = false,
                    state = uiState.error?.let { DataState.Error(it) },
                    hint = stringResource(Res.string.Contacts_NameHint),
                    onValueChange = viewModel::onNameChange
                )

                Addresses(
                    addressViewItems = uiState.addressViewItems,
                    onClickAddress = onNavigateToAddress
                )

                ActionButtons(
                    onAddAddress = { onNavigateToAddress(null) },
                    showDelete = uiState.showDelete,
                    onDeleteContact = {
                        bottomSheetType = ContactScreenBottomSheetType.DeleteConfirmation
                        coroutineScope.launch {
                            modalBottomSheetState.show()
                        }
                    }
                )

                VSpacer(32.dp)
            }
        }
    }
}

@Composable
private fun ActionButtons(
    onAddAddress: () -> Unit,
    showDelete: Boolean,
    onDeleteContact: () -> Unit
) {
    Spacer(Modifier.height(32.dp))
    CellUniversalLawrenceSection(
        buildList {
            add {
                AddAddressButton(onAddAddress)
            }
            if (showDelete) {
                add {
                    DeleteContactButton(onDeleteContact)
                }
            }
        }
    )
}

@Composable
private fun DeleteContactButton(onClick: () -> Unit) {
    RowUniversal(
        onClick = onClick
    ) {
        Icon(
            modifier = Modifier.padding(horizontal = 16.dp),
            painter = painterResource(Res.drawable.ic_delete_20),
            contentDescription = null,
            tint = ComposeAppTheme.colors.statusError
        )
        body_lucian(
            text = stringResource(Res.string.Contacts_DeleteContact),
        )
    }
}

@Composable
private fun AddAddressButton(onClick: () -> Unit) {
    RowUniversal(
        onClick = onClick
    ) {
        Icon(
            modifier = Modifier.padding(horizontal = 16.dp),
            painter = painterResource(Res.drawable.ic_plus),
            contentDescription = null,
            tint = ComposeAppTheme.colors.brandDefault
        )
        body_brand(text = stringResource(Res.string.Contacts_AddAddress))
    }
}

@Composable
private fun Addresses(
    addressViewItems: List<AddressViewItem>,
    onClickAddress: (ContactAddress) -> Unit
) {
    if (addressViewItems.isNotEmpty()) {
        Spacer(Modifier.height(32.dp))
        CellUniversalLawrenceSection(addressViewItems) { addressViewItem ->
            ContactAddress(addressViewItem) {
                onClickAddress(addressViewItem.contactAddress)
            }
        }
    }
}

@Composable
private fun ContactAddress(
    addressViewItem: AddressViewItem,
    onClickEdit: () -> Unit
) {
    RowUniversal(
        modifier = Modifier.padding(horizontal = 16.dp),
        onClick = onClickEdit
    ) {
        Image(
            modifier = Modifier
                .padding(end = 16.dp)
                .size(32.dp),
            painter = rememberAsyncImagePainterWithFallback(
                model = addressViewItem.blockchain.type.imageUrl,
                error = painterResource(Res.drawable.ic_platform_placeholder_32)
            ),
            contentDescription = null,
        )
        Column(modifier = Modifier.weight(1f)) {
            body_leah(text = addressViewItem.blockchain.name)
            subhead2_grey(text = addressViewItem.contactAddress.address)
        }
        Spacer(Modifier.width(9.dp))
        Icon(
            painter = painterResource(Res.drawable.ic_edit_20),
            contentDescription = null,
            tint = if (addressViewItem.edited) {
                ComposeAppTheme.colors.brandDefault
            } else {
                ComposeAppTheme.colors.iconSecondary
            }
        )
    }
}
