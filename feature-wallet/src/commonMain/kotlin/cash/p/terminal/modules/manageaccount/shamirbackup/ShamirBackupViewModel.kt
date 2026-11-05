package cash.p.terminal.modules.manageaccount.shamirbackup

import cash.p.terminal.core.IRandomProvider
import cash.p.terminal.modules.manageaccount.backupconfirmkey.WordCheckState
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.slip39.EncryptedMasterSecret
import cash.p.terminal.wallet.slip39.Slip39Shamir
import io.horizontalsystems.core.ViewModelUiState
import java.security.SecureRandom

/** Splits a SLIP-39 account into a new share set; the shares live only in this view model. */
class ShamirBackupViewModel(
    private val accountId: String,
    private val accountManager: IAccountManager,
    private val randomProvider: IRandomProvider,
    private val random: SecureRandom,
) : ViewModelUiState<ShamirBackupUiState>() {

    // Null once the account is deleted while the flow is open (unlink finishes in the background).
    private val accountType = accountManager.account(accountId)?.let { checkNotNull(it.type as? AccountType.Slip39) }
    private var scheme = ShamirScheme.PRESETS.first()
    private var shares: List<List<String>> = emptyList()
    private var confirmedCount = 0
    private var writtenDown = false
    private var check: WordCheckState? = null

    override fun createState() = ShamirBackupUiState(
        scheme = scheme,
        showPassphraseWarning = accountType?.passphrase?.isNotEmpty() == true,
        shares = shares,
        confirmedCount = confirmedCount,
        writtenDown = writtenDown,
        check = check,
        accountMissing = accountType == null,
    )

    fun onSelectTotal(total: Int) =
        updateScheme(ShamirScheme(threshold = minOf(scheme.threshold, total), total = total))

    fun onSelectThreshold(threshold: Int) = updateScheme(scheme.copy(threshold = threshold))

    fun onSelectPreset(preset: ShamirScheme) = updateScheme(preset)

    /** The set is generated once per scheme: coming back to the shares step shows the same shares. */
    fun onShowShares() {
        val words = accountType?.words ?: return
        if (shares.isNotEmpty()) return
        shares = Slip39Shamir.split(newSetSecret(words), scheme.threshold, scheme.total, random)
        emitState()
    }

    fun onWrittenDownChange(checked: Boolean) {
        writtenDown = checked
        emitState()
    }

    fun onStartCheck() {
        val share = shares.getOrNull(confirmedCount) ?: return
        if (!writtenDown) return
        check = WordCheckState.create(share, randomProvider)
        emitState()
    }

    fun onSelectWord(optionIndex: Int) {
        val current = check?.takeIf { !it.isComplete } ?: return
        check = current.select(optionIndex)
        if (check?.isComplete == true) onShareConfirmed()
        emitState()
    }

    override fun onCleared() {
        clearShares()
    }

    private fun onShareConfirmed() {
        confirmedCount++
        writtenDown = false
        if (confirmedCount == shares.size) accountManager.markAsBackedUp(accountId)
    }

    private fun updateScheme(newScheme: ShamirScheme) {
        if (!newScheme.isValid || newScheme == scheme) return
        scheme = newScheme
        clearShares()
        emitState()
    }

    private fun clearShares() {
        shares = emptyList()
        confirmedCount = 0
        writtenDown = false
        check = null
    }

    // A non-extendable set salts its cipher with the identifier, so only an extendable set may take a new one.
    private fun newSetSecret(accountWords: List<String>): EncryptedMasterSecret {
        val secret = Slip39Shamir.combine(listOf(Slip39Shamir.parseShare(accountWords.joinToString(" "))))
        if (!secret.extendable) return secret
        return EncryptedMasterSecret(
            identifier = Slip39Shamir.randomIdentifier(random),
            extendable = true,
            iterationExponent = secret.iterationExponent,
            ciphertext = secret.ciphertext,
        )
    }
}

data class ShamirScheme(val threshold: Int, val total: Int) {
    val isValid: Boolean get() = total in TOTAL_RANGE && threshold in thresholdOptions
    val thresholdOptions: IntRange get() = MIN_THRESHOLD..total

    companion object {
        private const val MIN_THRESHOLD = 2
        val TOTAL_RANGE = MIN_THRESHOLD..16
        val PRESETS = listOf(ShamirScheme(2, 3), ShamirScheme(3, 5), ShamirScheme(4, 7))
    }
}

data class ShamirBackupUiState(
    val scheme: ShamirScheme,
    val showPassphraseWarning: Boolean,
    val shares: List<List<String>>,
    val confirmedCount: Int,
    val writtenDown: Boolean,
    val check: WordCheckState?,
    val accountMissing: Boolean = false,
) {
    val currentSharePhrase: String? get() = shares.getOrNull(confirmedCount)?.joinToString(" ")
    val allConfirmed: Boolean get() = shares.isNotEmpty() && confirmedCount == shares.size
}
