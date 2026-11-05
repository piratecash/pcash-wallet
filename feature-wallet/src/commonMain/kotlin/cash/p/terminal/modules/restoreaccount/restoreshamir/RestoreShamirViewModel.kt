package cash.p.terminal.modules.restoreaccount.restoreshamir

import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.IAccountFactory
import cash.p.terminal.core.managers.SeedPhraseQrCrypto
import cash.p.terminal.modules.restoreaccount.restoremnemonic.PhraseInputProcessor
import cash.p.terminal.modules.restoreaccount.restoremnemonic.PhraseInputState
import cash.p.terminal.modules.restoreaccount.restoremnemonic.RestoreMnemonicModule.WordSuggestions
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Restore_Error_EmptyPassphrase
import cash.p.terminal.resources.shamir_error_duplicate
import cash.p.terminal.resources.shamir_error_invalid_share
import cash.p.terminal.resources.shamir_error_invalid_words
import cash.p.terminal.resources.shamir_error_mixed_sets
import cash.p.terminal.resources.shamir_error_other_set
import cash.p.terminal.resources.shamir_error_passphrase_chars
import cash.p.terminal.resources.shamir_error_word_count
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.slip39.Slip39AccountTypeFactory
import cash.p.terminal.wallet.slip39.Slip39Exception
import cash.p.terminal.wallet.slip39.Slip39RecoveryState
import cash.p.terminal.wallet.slip39.Slip39RecoveryState.AddResult
import cash.p.terminal.wallet.slip39.Slip39Shamir
import cash.p.terminal.wallet.slip39.Slip39Share
import cash.p.terminal.wallet.slip39.Slip39Wordlist
import io.horizontalsystems.core.IThirdKeyboard
import io.horizontalsystems.core.ViewModelUiState
import io.horizontalsystems.hdwalletkit.MnemonicWordList
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import java.security.SecureRandom

class RestoreShamirViewModel(
    accountFactory: IAccountFactory,
    private val slip39AccountTypeFactory: Slip39AccountTypeFactory,
    private val seedPhraseQrCrypto: SeedPhraseQrCrypto,
    thirdKeyboardStorage: IThirdKeyboard,
) : ViewModelUiState<RestoreShamirUiState>(), IThirdKeyboard by thirdKeyboardStorage {

    val defaultName = accountFactory.getNextAccountName()
    var accountName: String = defaultName
        get() = field.ifBlank { defaultName }
        private set

    private var input = ""
    private var cursorPosition = 0
    private var phraseInput = PhraseInputState()
    private var inputError: StringResource? = null
    private var recovery = Slip39RecoveryState()
    private var acceptedShares: List<Slip39Share> = emptyList()
    private var canonicalShare: List<String>? = null
    private var passphraseEnabled = false
    private var passphrase = ""
    private var passphraseError: StringResource? = null
    private var restoring = false
    private var accountType: AccountType.Slip39? = null
    private var prefillApplied = false

    override fun createState() = RestoreShamirUiState(
        defaultAccountName = defaultName,
        input = input,
        invalidWordRanges = phraseInput.invalidWordRanges,
        wordSuggestions = phraseInput.wordSuggestions,
        inputError = inputError,
        acceptedCount = acceptedShares.size,
        progress = progress(),
        isComplete = canonicalShare != null,
        passphraseEnabled = passphraseEnabled,
        passphrase = passphrase,
        passphraseError = passphraseError,
        restoring = restoring,
        accountType = accountType,
    )

    /** A scanned share opens this screen once; it never overrides what the user entered afterwards. */
    fun applyPrefill(prefill: ShamirPrefill) {
        if (prefillApplied) return
        prefillApplied = true
        applyShare(prefill)
    }

    /** An encrypted QR must carry a SLIP-39 share; plain text is entered as the next share. */
    fun onScanned(text: String) {
        if (!text.startsWith(SeedPhraseQrCrypto.QR_PREFIX)) {
            onEnterShare(text, text.length)
            onSubmitShare()
            return
        }
        val prefill = seedPhraseQrCrypto.decrypt(text).getOrNull()?.toShamirPrefillOrNull()
        if (prefill == null) {
            inputError = Res.string.shamir_error_invalid_share
            emitState()
        } else {
            applyShare(prefill)
        }
    }

    fun onEnterShare(text: String, cursorPosition: Int) {
        input = text
        this.cursorPosition = cursorPosition
        inputError = null
        phraseInput = PhraseInputProcessor.process(PhraseInputProcessor.wordItems(text), cursorPosition, WORD_LIST)
        emitState()
    }

    fun onContinue() {
        if (canonicalShare == null) onSubmitShare() else onRestore()
    }

    fun onSubmitShare() {
        when {
            phraseInput.wordItems.isEmpty() -> return
            phraseInput.invalidWordItems.isNotEmpty() -> {
                phraseInput = phraseInput.copy(invalidWordRanges = phraseInput.invalidWordItems.map { it.range })
                inputError = Res.string.shamir_error_invalid_words
            }

            else -> submit(phraseInput.wordItems.joinToString(" ") { it.word })
        }
        emitState()
    }

    fun onEnterName(name: String) {
        if (restoring) return
        accountName = name
    }

    fun onTogglePassphrase(enabled: Boolean) {
        if (restoring) return
        passphraseEnabled = enabled
        passphrase = ""
        passphraseError = null
        emitState()
    }

    fun onEnterPassphrase(passphrase: String) {
        if (restoring) return
        this.passphrase = passphrase
        passphraseError = Res.string.shamir_error_passphrase_chars.takeUnless {
            Slip39Shamir.isValidPassphrase(passphrase)
        }
        emitState()
    }

    fun onRestore() {
        val share = canonicalShare ?: return
        if (restoring || passphraseError != null) return
        if (passphraseEnabled && passphrase.isBlank()) {
            passphraseError = Res.string.Restore_Error_EmptyPassphrase
            emitState()
            return
        }
        restoring = true
        emitState()
        viewModelScope.launch {
            accountType = slip39AccountTypeFactory.create(share, if (passphraseEnabled) passphrase else "")
            restoring = false
            emitState()
        }
    }

    fun onSelectCoinsShown() {
        accountType = null
        emitState()
    }

    // A rejected scan must not leave its passphrase behind for the set that is accepted.
    private fun applyShare(prefill: ShamirPrefill) {
        if (submit(prefill.words.joinToString(" ")) && prefill.passphrase.isNotEmpty()) {
            passphraseEnabled = true
            onEnterPassphrase(prefill.passphrase)
        }
        emitState()
    }

    /** Returns whether the share joined the accepted set. */
    private fun submit(mnemonic: String): Boolean {
        val share = try {
            Slip39Shamir.parseShare(mnemonic)
        } catch (e: Slip39Exception) {
            inputError = e.toErrorText()
            return false
        }
        return when (val result = recovery.add(share)) {
            is AddResult.Admitted -> {
                recovery = result.state
                acceptedShares = acceptedShares + share
                clearInput()
                completeIfReady()
            }

            is AddResult.Rejected -> {
                inputError = result.error.toErrorText()
                false
            }

            is AddResult.GroupInconsistent -> {
                recovery = result.state
                acceptedShares = acceptedShares.filter { it.groupIndex != result.groupIndex }
                clearInput()
                inputError = Res.string.shamir_error_mixed_sets
                false
            }
        }
    }

    // Complete groups from different multi-group sets pass every count check and fail only here;
    // false means the completed set proved mixed and was discarded.
    private fun completeIfReady(): Boolean {
        if (!recovery.isComplete) return true
        return try {
            val masterSecret = Slip39Shamir.combine(recovery.selectedForCombine())
            canonicalShare = Slip39Shamir.split(masterSecret, threshold = 1, count = 1, SecureRandom()).single()
            true
        } catch (_: Slip39Exception.InvalidDigest) {
            recovery = Slip39RecoveryState()
            acceptedShares = emptyList()
            inputError = Res.string.shamir_error_mixed_sets
            false
        }
    }

    private fun clearInput() {
        input = ""
        cursorPosition = 0
        phraseInput = PhraseInputState()
        inputError = null
    }

    private fun progress(): ShamirProgress? {
        val groupCount = recovery.groupCount ?: return null
        val groupThreshold = recovery.groupThreshold ?: return null
        if (groupCount == 1) {
            return ShamirProgress.SingleGroup(
                required = recovery.memberThreshold(0) ?: return null,
                accepted = recovery.receivedCount(0),
            )
        }
        val groups = (0 until groupCount).mapNotNull { index ->
            recovery.memberThreshold(index)?.let { GroupProgress(index + 1, recovery.receivedCount(index), it) }
        }
        return ShamirProgress.MultiGroup(
            groupThreshold = groupThreshold,
            completeGroups = groups.count { it.accepted >= it.required },
            groups = groups,
        )
    }

    private fun Slip39Exception.toErrorText(): StringResource = when (this) {
        is Slip39Exception.InvalidWord -> Res.string.shamir_error_invalid_words
        is Slip39Exception.InvalidLength -> Res.string.shamir_error_word_count
        is Slip39Exception.MismatchedSet -> Res.string.shamir_error_other_set
        is Slip39Exception.DuplicateShare -> Res.string.shamir_error_duplicate
        is Slip39Exception.InvalidDigest -> Res.string.shamir_error_mixed_sets
        is Slip39Exception.InvalidChecksum,
        is Slip39Exception.InvalidPadding,
        is Slip39Exception.InvalidGroupThreshold,
        is Slip39Exception.InsufficientShares -> Res.string.shamir_error_invalid_share
    }

    private companion object {
        val WORD_LIST = MnemonicWordList(Slip39Wordlist.words, false)
    }
}

data class RestoreShamirUiState(
    val defaultAccountName: String = "",
    val input: String = "",
    val invalidWordRanges: List<IntRange> = emptyList(),
    val wordSuggestions: WordSuggestions? = null,
    val inputError: StringResource? = null,
    val acceptedCount: Int = 0,
    val progress: ShamirProgress? = null,
    val isComplete: Boolean = false,
    val passphraseEnabled: Boolean = false,
    val passphrase: String = "",
    val passphraseError: StringResource? = null,
    val restoring: Boolean = false,
    val accountType: AccountType.Slip39? = null,
)

sealed interface ShamirProgress {
    data class SingleGroup(val required: Int, val accepted: Int) : ShamirProgress
    data class MultiGroup(
        val groupThreshold: Int,
        val completeGroups: Int,
        val groups: List<GroupProgress>,
    ) : ShamirProgress
}

data class GroupProgress(val number: Int, val accepted: Int, val required: Int)
