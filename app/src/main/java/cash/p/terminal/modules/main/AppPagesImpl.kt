package cash.p.terminal.modules.main

import cash.p.terminal.modules.backuplocal.BackupLocalPage
import cash.p.terminal.modules.coin.CoinPage
import cash.p.terminal.modules.createaccount.CreateAccountPage
import cash.p.terminal.modules.manageaccount.backupkey.BackupKeyPage
import cash.p.terminal.modules.multiswap.SwapPage
import cash.p.terminal.modules.qrscanner.QRScannerPage
import cash.p.terminal.modules.restoreaccount.RestoreAccountPage
import cash.p.terminal.navigation.AppPages
import cash.p.terminal.navigation.BackupKeyInput
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.QrScannerInput
import cash.p.terminal.ui_compose.CoinFragmentInput
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.navigation.WalletPages

class AppPagesImpl : AppPages, WalletPages {
    override fun coin(coinUid: String): HSPage = CoinPage(CoinFragmentInput(coinUid))
    override fun createAccount(): HSPage = CreateAccountPage(null)
    override fun restoreAccount(): HSPage = RestoreAccountPage(null)
    override fun backupKey(input: BackupKeyInput): HSPage = BackupKeyPage(input)
    override fun qrScanner(input: QrScannerInput): HSPage = QRScannerPage(input)
    override fun swap(tokenOut: Token): HSPage = SwapPage(tokenOut = tokenOut)
    override fun backupLocal(account: Account): HSPage = BackupLocalPage(account)
}
