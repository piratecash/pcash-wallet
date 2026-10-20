package cash.p.terminal.modules.restorelocal

import cash.p.terminal.ui_compose.entities.DataState
import cash.p.terminal.modules.backuplocal.fullbackup.SelectBackupItemsViewModel.OtherBackupViewItem
import cash.p.terminal.modules.backuplocal.fullbackup.SelectBackupItemsViewModel.WalletBackupViewItem
import cash.p.terminal.modules.declinedtokens.DeclinedTokensReview

object RestoreLocalModule {

    data class UiState(
        val passphraseState: DataState.Error?,
        val showButtonSpinner: Boolean,
        val parseError: Exception?,
        val showSelectCoins: cash.p.terminal.wallet.AccountType?,
        val manualBackup: Boolean,
        val restored: Boolean,
        var walletBackupViewItems: List<WalletBackupViewItem>,
        var otherBackupViewItems: List<OtherBackupViewItem>,
        val showBackupItems: Boolean,
        val tokenReview: DeclinedTokensReview?,
    )
}
