package cash.p.terminal.modules.manageaccount.recoveryphrase

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.WalletOnboardingStorage
import cash.p.terminal.core.managers.RestoreSettingsManager
import cash.p.terminal.core.managers.SeedPhraseQrCrypto
import cash.p.terminal.core.utils.Bip39LanguageDetector
import cash.p.terminal.core.utils.MoneroWalletSeedConverter
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.SeedAccountType
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.hdwalletkit.Language
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RecoveryPhraseViewModel(
    account: Account,
    recoveryPhraseType: RecoveryPhrasePage.RecoveryPhraseType,
    private val seedPhraseQrCrypto: SeedPhraseQrCrypto,
    private val localStorage: WalletOnboardingStorage,
    private val restoreSettingsManager: RestoreSettingsManager
) : ViewModel() {

    val safetyRulesAgreed: Boolean
        get() = localStorage.safetyRulesAgreed

    private val content = phraseContent(account, recoveryPhraseType)
    val words: List<String> = content.words
    val passphrase: String? = content.passphrase
    val wordsNumbered: List<RecoveryPhraseModule.WordNumbered> = words.mapIndexed { index, word ->
        RecoveryPhraseModule.WordNumbered(word, index + 1)
    }

    // Cached encrypted QR content - regenerated on demand
    var encryptedSeedQrContent by mutableStateOf("")
        private set

    // Error state for QR generation failure
    var qrGenerationError by mutableStateOf(false)
        private set

    init {
        // Generate initial encrypted QR content
        regenerateEncryptedQrContent()
    }

    private fun phraseContent(
        account: Account,
        recoveryPhraseType: RecoveryPhrasePage.RecoveryPhraseType
    ): PhraseContent {
        val type = account.type
        if (type is SeedAccountType && recoveryPhraseType == RecoveryPhrasePage.RecoveryPhraseType.Monero) {
            return PhraseContent(
                words = MoneroWalletSeedConverter.getLegacySeed(type),
                moneroHeight = restoreSettingsManager.settings(account, BlockchainType.Monero).birthdayHeight
            )
        }
        return when (type) {
            is AccountType.Mnemonic -> PhraseContent(
                words = type.words,
                passphrase = type.passphrase,
                languageHint = Bip39LanguageDetector.detectSingle(type.words)
            )

            is AccountType.Slip39 -> PhraseContent(
                words = type.words,
                passphrase = type.passphrase,
                kind = SeedPhraseQrCrypto.KIND_SLIP39
            )

            is AccountType.MnemonicMonero -> PhraseContent(words = type.words, moneroHeight = type.height)
            else -> PhraseContent(words = emptyList())
        }
    }

    /**
     * Regenerates the encrypted QR content with fresh time-based encryption.
     * Call this when hiding the QR code so next reveal has valid encryption.
     */
    fun regenerateEncryptedQrContent() {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val encrypted = seedPhraseQrCrypto.encrypt(
                    words = words,
                    passphrase = passphrase ?: "",
                    height = content.moneroHeight,
                    language = content.languageHint,
                    kind = content.kind
                )
                encryptedSeedQrContent = encrypted
                qrGenerationError = false
            } catch (e: Exception) {
                qrGenerationError = true
            }
        }
    }

    fun onQrGenerationErrorShown() {
        qrGenerationError = false
    }
}

/**
 * [languageHint] is the BIP39 wordlist embedded in the QR; null for Monero/SLIP-39 (separate wordlists)
 * or when detection is ambiguous — autodetect on the restoring side handles the unhinted case.
 */
private class PhraseContent(
    val words: List<String>,
    val passphrase: String? = null,
    val moneroHeight: Long? = null,
    val languageHint: Language? = null,
    val kind: String? = null,
)
