package cash.p.terminal.modules.pin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.resources.Button_Next
import cash.p.terminal.resources.DuressPinSelectAccounts_Description
import cash.p.terminal.resources.DuressPinSelectAccounts_SectionWallets_Title
import cash.p.terminal.resources.DuressPinSelectAccounts_SectionWatchWallets_Title
import cash.p.terminal.resources.DuressPinSelectAccounts_Title
import cash.p.terminal.resources.ManageAccount_BackupRequired_Title
import cash.p.terminal.resources.Res
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.ButtonsGroupWithShade
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HFillSpacer
import cash.p.terminal.ui_compose.components.HeaderText
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.HsCheckbox
import cash.p.terminal.ui_compose.components.InfoText
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.subhead2_lucian
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

class SetDuressPinSelectAccountsPage : HSPage() {
    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel = koinViewModel<SetDuressPinSelectAccountsViewModel>()
        val pages = koinInject<DuressSecretPages>()

        SetDuressPinSelectAccountsScreen(
            regularAccounts = viewModel.regularAccounts,
            watchAccounts = viewModel.watchAccounts,
            onNext = { selected -> navigation.slideFromRight(pages.setDuress(selected)) },
            onBack = navigation::navigateUpSafely,
        )
    }
}

@Composable
internal fun SetDuressPinSelectAccountsScreen(
    regularAccounts: List<Account>,
    watchAccounts: List<Account>,
    onNext: (List<String>) -> Unit,
    onBack: () -> Unit,
) {
    val selected = remember { mutableStateListOf<String>() }

    Scaffold(
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = {
            AppBar(
                title = stringResource(Res.string.DuressPinSelectAccounts_Title),
                navigationIcon = { HsBackButton(onClick = onBack) },
            )
        }
    ) { innerPaddings ->
        Column(
            modifier = Modifier
                .padding(innerPaddings)
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                InfoText(
                    text = stringResource(Res.string.DuressPinSelectAccounts_Description),
                    paddingBottom = 32.dp
                )

                if (regularAccounts.isNotEmpty()) {
                    ItemsSection(
                        title = stringResource(Res.string.DuressPinSelectAccounts_SectionWallets_Title),
                        items = regularAccounts,
                        selected = selected
                    )
                    VSpacer(height = 32.dp)
                }

                if (watchAccounts.isNotEmpty()) {
                    ItemsSection(
                        title = stringResource(Res.string.DuressPinSelectAccounts_SectionWatchWallets_Title),
                        items = watchAccounts,
                        selected = selected
                    )
                    VSpacer(height = 32.dp)
                }
            }
            ButtonsGroupWithShade {
                ButtonPrimaryYellow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp),
                    title = stringResource(Res.string.Button_Next),
                    onClick = { onNext(selected.toList()) },
                )
            }
        }
    }
}

@Composable
private fun ItemsSection(
    title: String,
    items: List<Account>,
    selected: SnapshotStateList<String>,
) {
    fun toggle(account: Account, checked: Boolean) {
        if (checked) selected.add(account.id) else selected.remove(account.id)
    }

    HeaderText(text = title)
    CellUniversalLawrenceSection(items) { account ->
        val checked = selected.contains(account.id)
        RowUniversal(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .clickable { toggle(account, !checked) }
        ) {
            Column {
                body_leah(text = account.name)
                VSpacer(height = 1.dp)
                if (account.supportsBackup && !account.hasAnyBackup) {
                    subhead2_lucian(text = stringResource(Res.string.ManageAccount_BackupRequired_Title))
                } else {
                    subhead2_grey(
                        text = account.type.detailedDescription,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1
                    )
                }
            }
            HFillSpacer(minWidth = 8.dp)
            HsCheckbox(
                checked = checked,
                onCheckedChange = { toggle(account, it) },
            )
        }
    }
}

@Preview
@Composable
private fun SetDuressPinSelectAccountsScreenPreview() {
    val account = Account(
        id = "1",
        name = "Main Wallet",
        type = AccountType.Mnemonic(emptyList(), ""),
        origin = AccountOrigin.Created,
        level = 0
    )

    ComposeAppTheme {
        SetDuressPinSelectAccountsScreen(
            regularAccounts = List(3) { account.copy(id = "regular$it") },
            watchAccounts = List(2) { account.copy(id = "watch$it") },
            onNext = {},
            onBack = {},
        )
    }
}
