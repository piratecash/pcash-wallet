package cash.p.terminal.modules.backuplocal

import androidx.compose.runtime.Composable
import cash.p.terminal.modules.backuplocal.fullbackup.SelectBackupItemsScreen
import cash.p.terminal.modules.backuplocal.password.BackupType
import cash.p.terminal.modules.backuplocal.password.LocalBackupPasswordScreen
import cash.p.terminal.modules.backuplocal.terms.LocalBackupTermsScreen
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.navigation.removeLastUntilSafely
import cash.p.terminal.wallet.Account

class BackupLocalPage(val input: Account?) : HSPage(screenshotEnabled = false) {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        if (input != null) {
            LocalBackupTermsScreen(
                onTermsAccepted = {
                    navigation.slideFromRight(LocalBackupPasswordPage(BackupType.SingleWalletBackup(input.id)))
                },
                onBackClick = navigation::navigateUpSafely
            )
        } else {
            SelectBackupItemsScreen(
                onNextClick = { accountIds ->
                    navigation.slideFromRight(LocalBackupTermsPage(accountIds))
                },
                onBackClick = navigation::navigateUpSafely
            )
        }
    }
}

class LocalBackupTermsPage(val accountIds: List<String>) : HSPage(screenshotEnabled = false) {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        LocalBackupTermsScreen(
            onTermsAccepted = {
                navigation.slideFromRight(LocalBackupPasswordPage(BackupType.FullBackup(accountIds)))
            },
            onBackClick = navigation::navigateUpSafely
        )
    }
}

class LocalBackupPasswordPage(val backupType: BackupType) : HSPage(screenshotEnabled = false) {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        LocalBackupPasswordScreen(
            backupType = backupType,
            navigation = navigation,
            onBackClick = navigation::navigateUpSafely,
            onFinish = { navigation.removeLastUntilSafely(BackupLocalPage::class, true) }
        )
    }
}
