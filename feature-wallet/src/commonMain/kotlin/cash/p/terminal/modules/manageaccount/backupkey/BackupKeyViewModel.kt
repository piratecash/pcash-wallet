package cash.p.terminal.modules.manageaccount.backupkey

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import cash.p.terminal.modules.manageaccount.recoveryphrase.RecoveryPhraseModule
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.SeedAccountType

class BackupKeyViewModel(
    accountId: String,
    accountManager: IAccountManager
) : ViewModel() {

    val account: Account? = accountManager.account(accountId)

    var passphrase by mutableStateOf("")
        private set

    var showPassphraseBlock by mutableStateOf(true)
        private set

    var wordsNumbered by mutableStateOf<List<RecoveryPhraseModule.WordNumbered>>(listOf())
        private set

    var accountNotFound by mutableStateOf(false)
        private set

    init {
        if (account == null) {
            accountNotFound = true
        } else {
            showPassphraseBlock = account.type is SeedAccountType

            account.type.manualBackupPhrase()?.let { (words, passphrase) ->
                wordsNumbered = words.mapIndexed { index, word ->
                    RecoveryPhraseModule.WordNumbered(word, index + 1)
                }
                this.passphrase = passphrase
            }
        }
    }
}

/** The words a manual backup shows and checks, paired with the passphrase they are used with. */
internal fun AccountType.manualBackupPhrase(): Pair<List<String>, String>? = when (this) {
    is AccountType.Mnemonic -> words to passphrase
    is AccountType.Slip39 -> words to passphrase
    is AccountType.MnemonicMonero -> words to password
    else -> null
}
