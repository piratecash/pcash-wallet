package cash.p.terminal.modules.multiswap.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.entities.Address
import cash.p.terminal.modules.fee.QuoteInfoRow
import cash.p.terminal.ui_compose.components.ButtonSecondaryCircle
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.subhead2_leah
import cash.p.terminal.core.IClipboardManager
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import io.horizontalsystems.core.Translator
import io.horizontalsystems.core.entities.BlockchainType
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Hud_Text_Copied
import cash.p.terminal.resources.Swap_Recipient
import cash.p.terminal.resources.TransactionInfo_ContactName
import cash.p.terminal.resources.ic_copy_20
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.modules.contacts.ContactsRepository

data class DataFieldRecipientExtended(
    val address: Address,
    val blockchainType: BlockchainType
) : DataField {
    @Composable
    override fun GetContent(navigation: HSNavigation, borderTop: Boolean) {
        QuoteInfoRow(
            borderTop = borderTop,
            title = {
                subhead2_grey(text = stringResource(Res.string.Swap_Recipient))
            },
            value = {
                subhead2_leah(
                    modifier = Modifier.weight(1f, false),
                    text = address.hex,
                    textAlign = TextAlign.End
                )

                val hud = rememberHudMessenger()
                HSpacer(16.dp)
                ButtonSecondaryCircle(
                    icon = Res.drawable.ic_copy_20,
                    onClick = {
                        getKoinInstance<IClipboardManager>().copyText(address.hex)
                        hud.showSuccess(Translator.getString(Res.string.Hud_Text_Copied))
                    }
                )
            }
        )

        val contact = getKoinInstance<ContactsRepository>().getContactsFiltered(
            blockchainType,
            addressQuery = address.hex
        ).firstOrNull()

        contact?.name?.let { name ->
            QuoteInfoRow(
                borderTop = borderTop,
                title = {
                    subhead2_grey(text = stringResource(Res.string.TransactionInfo_ContactName))
                },
                value = {
                    subhead2_leah(
                        text = name,
                        textAlign = TextAlign.End
                    )
                }
            )
        }
    }
}
