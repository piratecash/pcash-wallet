package cash.p.terminal.modules.backuplocal.fullbackup

import cash.p.terminal.resources.BackupManager_AppSettingsDescription
import cash.p.terminal.resources.BackupManager_AppSettingsTitle
import cash.p.terminal.resources.BackupManager_CustomRpc
import cash.p.terminal.resources.BackupManager_WatchWallets
import cash.p.terminal.resources.BackupManager_Watchlist
import cash.p.terminal.resources.Contacts
import cash.p.terminal.resources.Res
import io.horizontalsystems.core.Translator

class BackupViewItemFactory {

    fun backupViewItems(
        backupItems: BackupItems
    ): Pair<
        List<SelectBackupItemsViewModel.WalletBackupViewItem>,
        List<SelectBackupItemsViewModel.OtherBackupViewItem>,
    > {

        val walletBackupViewItems = backupItems.accounts.map { account ->
            SelectBackupItemsViewModel.WalletBackupViewItem(
                account = account,
                name = account.name,
                type = account.type.detailedDescription,
                backupRequired = !account.hasAnyBackup,
                selected = true
            )
        }

        val otherBackupViewItems = buildList {
            backupItems.watchWallets?.let {
                add(
                    SelectBackupItemsViewModel.OtherBackupViewItem(
                        title = Translator.getString(
                            Res.string.BackupManager_WatchWallets
                        ), value = "$it"
                    )
                )
            }
            backupItems.watchlist?.let {
                add(
                    SelectBackupItemsViewModel.OtherBackupViewItem(
                        title = Translator.getString(Res.string.BackupManager_Watchlist),
                        value = "$it"
                    )
                )
            }
            backupItems.contacts?.let {
                add(
                    SelectBackupItemsViewModel.OtherBackupViewItem(
                        title = Translator.getString(Res.string.Contacts), value = "$it"
                    )
                )
            }
            backupItems.customRpc?.let {
                add(
                    SelectBackupItemsViewModel.OtherBackupViewItem(
                        title = Translator.getString(Res.string.BackupManager_CustomRpc),
                        value = "$it"
                    )
                )
            }
            add(
                SelectBackupItemsViewModel.OtherBackupViewItem(
                    title = Translator.getString(
                        Res.string.BackupManager_AppSettingsTitle
                    ),
                    subtitle = Translator.getString(
                        Res.string.BackupManager_AppSettingsDescription
                    )
                )
            )
        }

        return Pair(walletBackupViewItems, otherBackupViewItems)
    }

}
