package cash.p.terminal.modules.transactions

import io.horizontalsystems.core.common.CommonParcelable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.modules.contacts.model.Contact
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.HFillSpacer
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui.compose.components.InfoErrorMessageDefault
import cash.p.terminal.ui_compose.components.InfoText
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.CellUniversal
import cash.p.terminal.ui_compose.components.title3_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.common.CommonParcelize
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Contacts
import cash.p.terminal.resources.SelectContacts_All
import cash.p.terminal.resources.Transactions_Filter_ChooseContact_Hint
import cash.p.terminal.resources.Transactions_Filter_ChooseContact_NoSuitableContact
import cash.p.terminal.resources.ic_user_24
import cash.p.terminal.resources.icon_check_1_24
import cash.p.terminal.resources.icon_paper_contract_24

class SelectContactPage(val input: Input) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        SelectContactScreen(navigation, this, input)
    }

    @CommonParcelize
    data class Input(val selected: Contact?, val blockchainType: BlockchainType?) : CommonParcelable

    @CommonParcelize
    data class Result(val contact: Contact?) : CommonParcelable

}

@Composable
fun SelectContactScreen(navigation: HSNavigation, page: SelectContactPage, input: SelectContactPage.Input) {
    val viewModel = koinViewModel<SelectContactViewModel> { parametersOf(input.selected, input.blockchainType) }
    val uiState = viewModel.uiState

    Scaffold(
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = {
            AppBar(
                title = {
                    title3_leah(text = stringResource(Res.string.Contacts))
                },
                navigationIcon = {
                    HsBackButton(onClick = navigation::navigateUpSafely)
                },
            )
        }
    ) {
        if (uiState.items.isEmpty()) {
            Column(modifier = Modifier.padding(it)) {
                InfoText(text = stringResource(Res.string.Transactions_Filter_ChooseContact_Hint))
                InfoErrorMessageDefault(
                    painter = painterResource(Res.drawable.ic_user_24),
                    text = stringResource(Res.string.Transactions_Filter_ChooseContact_NoSuitableContact)
                )
            }
        } else {
            LazyColumn(modifier = Modifier.padding(it)) {
                item {
                    InfoText(text = stringResource(Res.string.Transactions_Filter_ChooseContact_Hint))
                }
                items(uiState.items) { contact ->
                    CellContact(contact, uiState.selected) {
                        navigation.setResult(page, SelectContactPage.Result(contact))
                        navigation.navigateUpSafely()
                    }
                }
                item {
                    VSpacer(height = 32.dp)
                }
            }
        }
    }
}

@Composable
private fun CellContact(
    contact: Contact?,
    selected: Contact?,
    onClick: () -> Unit,
) {
    CellUniversal(
        onClick = onClick
    ) {
        Icon(
            painter = if (contact == null) {
                painterResource(Res.drawable.icon_paper_contract_24)
            } else {
                painterResource(Res.drawable.ic_user_24)
            },
            contentDescription = "",
            tint = ComposeAppTheme.colors.iconSecondary
        )
        HSpacer(width = 16.dp)
        body_leah(text = contact?.name ?: stringResource(Res.string.SelectContacts_All))
        if (contact == selected) {
            HFillSpacer(minWidth = 8.dp)
            Icon(
                painter = painterResource(Res.drawable.icon_check_1_24),
                contentDescription = "selected",
                tint = ComposeAppTheme.colors.brandDefault
            )
        }
    }
}
