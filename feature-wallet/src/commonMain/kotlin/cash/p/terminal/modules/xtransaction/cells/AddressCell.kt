package cash.p.terminal.modules.xtransaction.cells

import androidx.compose.runtime.Composable
import cash.p.terminal.resources.icon_20_user_plus
import cash.p.terminal.resources.ic_copy_20
import cash.p.terminal.resources.Hud_Text_Copied
import cash.p.terminal.resources.Contacts_AddAddress
import cash.p.terminal.resources.Res
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.contacts.ContactsModule
import cash.p.terminal.modules.contacts.ContactsPage
import cash.p.terminal.modules.contacts.Mode
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.ui_compose.components.AppSelectorDialog
import cash.p.terminal.ui_compose.components.AppSelectorItem
import cash.p.terminal.core.IClipboardManager
import cash.p.terminal.ui_compose.components.ButtonSecondaryCircle
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.subhead1_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.CellUniversal
import io.horizontalsystems.core.entities.BlockchainType
import cash.p.terminal.ui_compose.components.LocalHudMessenger
import org.koin.compose.koinInject

@Composable
fun AddressCell(
    title: String,
    value: String,
    showAddContactButton: Boolean,
    blockchainType: BlockchainType?,
    navigation: HSNavigation? = null,
    borderTop: Boolean = true
) {
    val hud = LocalHudMessenger.current
    val clipboardManager = koinInject<IClipboardManager>()
    val copiedMessage = stringResource(Res.string.Hud_Text_Copied)
    var showSaveAddressDialog by remember { mutableStateOf(false) }
    CellUniversal(borderTop = borderTop) {
        subhead2_grey(text = title)

        HSpacer(16.dp)
        subhead1_leah(
            modifier = Modifier.weight(1f),
            text = value,
            textAlign = TextAlign.Right
        )

        if (showAddContactButton) {
            HSpacer(16.dp)
            ButtonSecondaryCircle(
                icon = Res.drawable.icon_20_user_plus,
                onClick = { showSaveAddressDialog = true }
            )
        }

        HSpacer(16.dp)
        ButtonSecondaryCircle(
            icon = Res.drawable.ic_copy_20,
            onClick = {
                clipboardManager.copyText(value)
                hud.showSuccess(copiedMessage)
            }
        )
    }

    if (showSaveAddressDialog) {
        AppSelectorDialog(
            title = stringResource(Res.string.Contacts_AddAddress),
            items = ContactsModule.AddAddressAction.entries.map {
                AppSelectorItem(stringResource(it.title), false, it)
            },
            onDismiss = {
                showSaveAddressDialog = false
            },
            onSelect = { action ->
                blockchainType?.let {
                    val mode = when (action) {
                        ContactsModule.AddAddressAction.AddToNewContact -> {
                            Mode.AddAddressToNewContact(
                                blockchainType,
                                value
                            )
                        }

                        ContactsModule.AddAddressAction.AddToExistingContact -> {
                            Mode.AddAddressToExistingContact(
                                blockchainType,
                                value
                            )
                        }
                    }
                    navigation?.slideFromRight(ContactsPage(ContactsPage.Input(mode)))
                }
            })
    }
}
