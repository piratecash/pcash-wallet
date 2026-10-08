package cash.p.terminal.navigation

/** Pages of `:app` that feature modules open without depending on `:app`. */
interface AppPages {
    fun coin(coinUid: String): HSPage
    fun createAccount(): HSPage
    fun restoreAccount(): HSPage
    fun backupKey(input: BackupKeyInput): HSPage
    fun qrScanner(input: QrScannerInput): HSPage
}
