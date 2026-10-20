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
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import cash.p.terminal.core.navigateWithTermsAccepted
import cash.p.terminal.modules.backupalert.BackupAlert
import cash.p.terminal.modules.createaccount.CreateAccountPage
import cash.p.terminal.feature.wallet.WalletPlatformPages
import org.koin.compose.koinInject
import cash.p.terminal.modules.manageaccount.ManageAccountPage
import cash.p.terminal.modules.manageaccounts.ManageAccountsModule.AccountViewItem
import cash.p.terminal.modules.manageaccounts.ManageAccountsModule.ActionViewItem
import cash.p.terminal.modules.importwallet.ImportWalletPage
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
import cash.p.terminal.ui_compose.components.body_brand
import cash.p.terminal.ui_compose.components.body_disabled
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.subhead2_lucian

import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_card
import cash.p.terminal.resources.ic_switch_wallet_24
import cash.p.terminal.resources.icon_binocule_20
import cash.p.terminal.resources.ManageAccount_BackupRequired_Title
import cash.p.terminal.resources.ManageAccount_MigrationRequired_Title
import cash.p.terminal.resources.ManageAccounts_CreateNewWallet
import cash.p.terminal.resources.ManageAccounts_ImportWallet
import cash.p.terminal.resources.ManageAccounts_Title
import cash.p.terminal.resources.ManageAccounts_WatchAddress
import cash.p.terminal.resources.hardware_wallet
import cash.p.terminal.resources.ic_download_20
import cash.p.terminal.resources.ic_more2_20
import cash.p.terminal.resources.ic_plus
import cash.p.terminal.resources.icon_warning_2_20
import cash.p.terminal.resources.manage_accounts_premium_active
import cash.p.terminal.resources.manage_accounts_section_hardware
import cash.p.terminal.resources.manage_accounts_section_other
import cash.p.terminal.resources.manage_accounts_section_watch

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
    val walletPlatformPages = koinInject<WalletPlatformPages>()

    val finish = viewModel.finish

    if (finish) {
        navigation.navigateUp()
    }

    Column(
        modifier = Modifier
            .background(color = ComposeAppTheme.colors.backgroundBase)
            .navigationBarsPadding()
    ) {
        AppBar(
            title = stringResource(Res.string.ManageAccounts_Title),
            navigationIcon = { HsBackButton(onClick = { navigation.navigateUpSafely() }) }
        )

        LazyColumn(modifier = Modifier.background(color = ComposeAppTheme.colors.backgroundBase)) {
            item {
                WalletSection(
                    accounts = viewModel.premiumAccountsState,
                    onSelect = viewModel::onSelect,
                    navigation = navigation,
                    frameColor = ComposeAppTheme.colors.brandDefault,
                    header = {
                        PremiumHeader(text = stringResource(Res.string.manage_accounts_premium_active))
                    }
                )
                WalletSection(
                    accounts = viewModel.regularAccountsState,
                    onSelect = viewModel::onSelect,
                    navigation = navigation,
                    header = {
                        SectionHeaderWithIcon(
                            iconRes = Res.drawable.ic_switch_wallet_24,
                            text = stringResource(Res.string.manage_accounts_section_other)
                        )
                    }
                )
                WalletSection(
                    accounts = viewModel.watchAccountsState,
                    onSelect = viewModel::onSelect,
                    navigation = navigation,
                    header = {
                        SectionHeaderWithIcon(
                            iconRes = Res.drawable.icon_binocule_20,
                            text = stringResource(Res.string.manage_accounts_section_watch)
                        )
                    }
                )
                WalletSection(
                    accounts = viewModel.hardwareAccountsState,
                    onSelect = viewModel::onSelect,
                    navigation = navigation,
                    header = {
                        SectionHeaderWithIcon(
                            iconRes = Res.drawable.ic_card,
                            text = stringResource(Res.string.manage_accounts_section_hardware)
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
                            Res.drawable.ic_plus,
                            Res.string.ManageAccounts_CreateNewWallet
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
                            Res.drawable.ic_download_20,
                            Res.string.ManageAccounts_ImportWallet
                        ) {
                            navigation.slideFromRight(ImportWalletPage(args))
                        })
                    add(
                        ActionViewItem(
                            Res.drawable.icon_binocule_20,
                            Res.string.ManageAccounts_WatchAddress
                        ) {
                            navigation.slideFromRight(WatchAddressPage(args))
                        })
                    if (walletPlatformPages.hardwareWallet(args) != null) {
                        add(
                            ActionViewItem(
                                icon = Res.drawable.ic_card,
                                title = Res.string.hardware_wallet,
                            ) {
                                walletPlatformPages.hardwareWallet(args)?.let { navigation.slideFromRight(it) }
                            }
                        )
                    }
                }

                CellUniversalLawrenceSection(actions) {
                    RowUniversal(
                        onClick = it.callback
                    ) {
                        Icon(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            painter = painterResource(it.icon),
                            contentDescription = null,
                            tint = if (it.enabled) {
                                ComposeAppTheme.colors.brandDefault
                            } else {
                                ComposeAppTheme.colors.iconDisabled
                            }
                        )
                        if (it.enabled) {
                            body_brand(text = stringResource(it.title))
                        } else {
                            body_disabled(text = stringResource(it.title))
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
            subhead2_lucian(text = stringResource(Res.string.ManageAccount_BackupRequired_Title))

        accountViewItem.migrationRequired ->
            subhead2_lucian(text = stringResource(Res.string.ManageAccount_MigrationRequired_Title))

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
        Res.drawable.icon_warning_2_20 to ComposeAppTheme.colors.statusError
    } else {
        Res.drawable.ic_more2_20 to ComposeAppTheme.colors.iconPrimary
    }
    ButtonSecondaryCircle(
        modifier = Modifier.padding(horizontal = 16.dp),
        icon = icon,
        tint = iconTint,
        onClick = {
            navigation.slideFromRight(
                ManageAccountPage(ManageAccountPage.Input(accountViewItem.accountId))
            )
        }
    )
}
