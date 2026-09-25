package cash.p.terminal.modules.manageaccounts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import cash.p.terminal.R
import cash.p.terminal.core.navigateWithTermsAccepted
import cash.p.terminal.modules.backupalert.BackupAlert
import cash.p.terminal.modules.createaccount.CreateAccountPage
import cash.p.terminal.modules.hardwarewallet.HardwareWalletPage
import cash.p.terminal.modules.importwallet.ImportWalletPage
import cash.p.terminal.modules.manageaccount.ManageAccountPage
import cash.p.terminal.modules.manageaccounts.ManageAccountsModule.AccountViewItem
import cash.p.terminal.modules.manageaccounts.ManageAccountsModule.ActionViewItem
import cash.p.terminal.modules.watchaddress.WatchAddressPage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.ui_compose.components.HsRadioButton
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonSecondaryCircle
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.PremiumHeader
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.SectionHeaderWithIcon
import cash.p.terminal.ui_compose.components.body_grey
import cash.p.terminal.ui_compose.components.body_jacob
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.subhead2_lucian

import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.navigation.navigateUpSafely

class ManageAccountsPage(val input: ManageAccountsModule.Mode) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        ManageAccountsScreen(navigation, input)
    }
}

@Composable
fun ManageAccountsScreen(navigation: HSNavigation, mode: ManageAccountsModule.Mode) {
    BackupAlert(navigation)

    val viewModel: ManageAccountsViewModel = koinViewModel { parametersOf(mode) }

    val finish = viewModel.finish

    if (finish) {
        navigation.navigateUp()
    }

    Column(
        modifier = Modifier
            .background(color = ComposeAppTheme.colors.tyler)
            .navigationBarsPadding()
    ) {
        AppBar(
            title = stringResource(R.string.ManageAccounts_Title),
            navigationIcon = { HsBackButton(onClick = { navigation.navigateUpSafely() }) }
        )

        LazyColumn(modifier = Modifier.background(color = ComposeAppTheme.colors.tyler)) {
            item {
                WalletSection(
                    accounts = viewModel.premiumAccountsState,
                    onSelect = viewModel::onSelect,
                    navigation = navigation,
                    frameColor = ComposeAppTheme.colors.jacob,
                    header = {
                        PremiumHeader(text = stringResource(R.string.manage_accounts_premium_active))
                    }
                )
                WalletSection(
                    accounts = viewModel.regularAccountsState,
                    onSelect = viewModel::onSelect,
                    navigation = navigation,
                    header = {
                        SectionHeaderWithIcon(
                            iconRes = R.drawable.ic_switch_wallet_24,
                            text = stringResource(R.string.manage_accounts_section_other)
                        )
                    }
                )
                WalletSection(
                    accounts = viewModel.watchAccountsState,
                    onSelect = viewModel::onSelect,
                    navigation = navigation,
                    header = {
                        SectionHeaderWithIcon(
                            iconRes = R.drawable.icon_binocule_20,
                            text = stringResource(R.string.manage_accounts_section_watch)
                        )
                    }
                )
                WalletSection(
                    accounts = viewModel.hardwareAccountsState,
                    onSelect = viewModel::onSelect,
                    navigation = navigation,
                    header = {
                        SectionHeaderWithIcon(
                            iconRes = R.drawable.ic_card,
                            text = stringResource(R.string.manage_accounts_section_hardware)
                        )
                    }
                )

                val args = when (mode) {
                    ManageAccountsModule.Mode.Manage -> ManageAccountsModule.Input(
                        ManageAccountsPage::class,
                        false
                    )

                    ManageAccountsModule.Mode.Switcher -> ManageAccountsModule.Input(
                        ManageAccountsPage::class,
                        true
                    )
                }

                val actions = buildList {
                    add(
                        ActionViewItem(
                            R.drawable.ic_plus,
                            R.string.ManageAccounts_CreateNewWallet
                        ) {
                            navigation.navigateWithTermsAccepted {
                                navigation.slideFromRight(
                                    CreateAccountPage(
                                        CreateAccountPage.Input(
                                            popOffOnSuccess = args.popOffOnSuccess,
                                            popOffInclusive = args.popOffInclusive
                                        )
                                    )
                                )
                            }
                        })
                    add(
                        ActionViewItem(
                            R.drawable.ic_download_20,
                            R.string.ManageAccounts_ImportWallet
                        ) {
                            navigation.slideFromRight(ImportWalletPage(args))
                        })
                    add(
                        ActionViewItem(
                            R.drawable.icon_binocule_20,
                            R.string.ManageAccounts_WatchAddress
                        ) {
                            navigation.slideFromRight(WatchAddressPage(args))
                        })
                    add(
                        ActionViewItem(
                            icon = R.drawable.ic_card,
                            title = R.string.hardware_wallet,
                        ) {
                            navigation.slideFromRight(HardwareWalletPage(args))
                        }
                    )
                }

                CellUniversalLawrenceSection(actions) {
                    RowUniversal(
                        onClick = it.callback
                    ) {
                        Icon(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            painter = painterResource(id = it.icon),
                            contentDescription = null,
                            tint = if (it.enabled) {
                                ComposeAppTheme.colors.jacob
                            } else {
                                ComposeAppTheme.colors.grey
                            }
                        )
                        if (it.enabled) {
                            body_jacob(text = stringResource(id = it.title))
                        } else {
                            body_grey(text = stringResource(id = it.title))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun WalletSection(
    accounts: List<AccountViewItem>?,
    onSelect: (AccountViewItem) -> Unit,
    navigation: HSNavigation,
    header: @Composable () -> Unit,
    frameColor: Color? = null,
) {
    if (!accounts.isNullOrEmpty()) {
        header()
        AccountsSection(accounts, onSelect, navigation, frameColor)
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun AccountsSection(
    accounts: List<AccountViewItem>,
    onSelect: (AccountViewItem) -> Unit,
    navigation: HSNavigation,
    frameColor: Color?,
) {
    if (frameColor != null) {
        CellUniversalLawrenceSection(items = accounts, frameColor = frameColor) { accountViewItem ->
            AccountRow(accountViewItem, onSelect, navigation)
        }
    } else {
        CellUniversalLawrenceSection(items = accounts) { accountViewItem ->
            AccountRow(accountViewItem, onSelect, navigation)
        }
    }
}

@Composable
private fun AccountRow(
    accountViewItem: AccountViewItem,
    onSelect: (AccountViewItem) -> Unit,
    navigation: HSNavigation,
) {
    RowUniversal(
        onClick = { onSelect(accountViewItem) }
    ) {
        HsRadioButton(
            modifier = Modifier.padding(horizontal = 4.dp),
            selected = accountViewItem.selected,
            onClick = { onSelect(accountViewItem) }
        )
        Column(modifier = Modifier.weight(1f)) {
            body_leah(text = accountViewItem.title)
            AccountSubtitle(accountViewItem)
        }
        PremiumBadge(
            premiumType = accountViewItem.premiumType,
            modifier = Modifier.padding(start = 8.dp)
        )
        AccountMoreButton(accountViewItem, navigation)
    }
}

@Composable
private fun AccountSubtitle(accountViewItem: AccountViewItem) {
    when {
        accountViewItem.backupRequired ->
            subhead2_lucian(text = stringResource(id = R.string.ManageAccount_BackupRequired_Title))

        accountViewItem.migrationRequired ->
            subhead2_lucian(text = stringResource(id = R.string.ManageAccount_MigrationRequired_Title))

        else -> subhead2_grey(
            text = accountViewItem.subtitle,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1
        )
    }
}

@Composable
private fun AccountMoreButton(
    accountViewItem: AccountViewItem,
    navigation: HSNavigation,
) {
    val (icon, iconTint) = if (accountViewItem.showAlertIcon) {
        R.drawable.icon_warning_2_20 to ComposeAppTheme.colors.lucian
    } else {
        R.drawable.ic_more2_20 to ComposeAppTheme.colors.leah
    }
    ButtonSecondaryCircle(
        modifier = Modifier.padding(horizontal = 16.dp),
        icon = icon,
        tint = iconTint
    ) {
        navigation.slideFromRight(
            ManageAccountPage(ManageAccountPage.Input(accountViewItem.accountId))
        )
    }
}
