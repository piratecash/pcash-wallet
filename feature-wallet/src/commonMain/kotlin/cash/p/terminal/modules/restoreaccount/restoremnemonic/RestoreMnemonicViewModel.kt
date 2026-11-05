package cash.p.terminal.modules.restoreaccount.restoremnemonic

import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.IAccountFactory
import cash.p.terminal.core.managers.SeedPhraseQrCrypto
import cash.p.terminal.core.managers.WalletActivator
import cash.p.terminal.core.managers.toSeedQrErrorMessage
import cash.p.terminal.core.usecase.MoneroWalletUseCase
import cash.p.terminal.core.usecase.ValidateMoneroHeightUseCase
import cash.p.terminal.core.usecase.ValidateMoneroMnemonicUseCase
import cash.p.terminal.core.utils.Bip39LanguageDetector
import cash.p.terminal.core.utils.MoneroConfig
import cash.p.terminal.modules.mnemonic.mnemonicLanguagesOrdered
import cash.p.terminal.modules.restoreaccount.restoreshamir.toShamirPrefillOrNull
import cash.p.terminal.modules.restoreaccount.restoremnemonic.RestoreMnemonicModule.UiState
import io.horizontalsystems.core.Translator
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.normalizeNFKD
import com.m2049r.xmrwallet.util.ledger.Monero
import io.horizontalsystems.core.IThirdKeyboard
import io.horizontalsystems.core.ViewModelUiState
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.hdwalletkit.Language
import io.horizontalsystems.hdwalletkit.Mnemonic
import io.horizontalsystems.hdwalletkit.MnemonicWordList
import io.horizontalsystems.hdwalletkit.WordList
import java.time.LocalDate
import kotlinx.coroutines.launch
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Restore_Error_EmptyPassphrase
import cash.p.terminal.resources.Restore_Error_MnemonicWordCount
import cash.p.terminal.resources.Restore_Error_MnemonicWordCount_monero
import cash.p.terminal.resources.Restore_InvalidChecksum
import cash.p.terminal.resources.invalid_height_format
import cash.p.terminal.resources.monero_restore_error

class RestoreMnemonicViewModel(
    private val validateMoneroMnemonicUseCase: ValidateMoneroMnemonicUseCase,
    private val validateMoneroHeightUseCase: ValidateMoneroHeightUseCase,
    private val moneroWalletUseCase: MoneroWalletUseCase,
    private val accountManager: IAccountManager,
    private val walletActivator: WalletActivator,
    private val seedPhraseQrCrypto: SeedPhraseQrCrypto,
    private val accountFactory: IAccountFactory,
    thirdKeyboardStorage: IThirdKeyboard
) : ViewModelUiState<UiState>(), IThirdKeyboard by thirdKeyboardStorage {

    val mnemonicLanguages = mnemonicLanguagesOrdered

    private var passphraseEnabled: Boolean = false
    private var passphrase: String = ""
    private var passphraseError: String? = null
    private var phraseInput = PhraseInputState()
    private var error: String? = null
    private var errorHeight: String? = null
    private var isMoneroMnemonic: Boolean = false
    private var height: String = ""
    private var accountType: AccountType? = null
    private var language = Language.English
    private var text = ""
    private var cursorPosition = 0
    private var normalMnemonicWordList = WordList.wordListStrict(language)
    private var mnemonicMoneroWordList = MnemonicWordList(Monero.ENGLISH_WORDS.toList(), false)

    private val mnemonicWordList: MnemonicWordList
        get() = if (isMoneroMnemonic) mnemonicMoneroWordList else normalMnemonicWordList


    val defaultName = accountFactory.getNextAccountName()
    var accountName: String = defaultName
        get() = field.ifBlank { defaultName }
        private set

    override fun createState() = UiState(
        passphraseEnabled = passphraseEnabled,
        passphrase = passphrase,
        passphraseError = passphraseError,
        invalidWordRanges = phraseInput.invalidWordRanges,
        error = error,
        errorHeight = errorHeight,
        height = height,
        isMoneroMnemonic = isMoneroMnemonic,
        accountType = accountType,
        wordSuggestions = phraseInput.wordSuggestions,
        language = displayedLanguage,
    )

    fun onToggleMoneroMnemonic(enabled: Boolean) {
        isMoneroMnemonic = enabled
        processText()
        emitState()
    }

    private fun processText() {
        val wordItems = PhraseInputProcessor.wordItems(text)

        if (!isMoneroMnemonic && wordItems.size >= MIN_WORDS_FOR_AUTODETECT) {
            autodetectLanguage(wordItems.map { it.word })
        }

        phraseInput = PhraseInputProcessor.process(wordItems, cursorPosition, mnemonicWordList)
    }

    fun onTogglePassphrase(enabled: Boolean) {
        passphraseEnabled = enabled
        passphrase = ""
        passphraseError = null
        passphraseError = null

        emitState()
    }

    fun onEnterPassphrase(passphrase: String) {
        this.passphrase = passphrase
        passphraseError = null

        emitState()
    }

    fun onEnterName(name: String) {
        accountName = name
    }

    fun onEnterMnemonicPhrase(text: String, cursorPosition: Int) {
        error = null
        this.text = text
        this.cursorPosition = cursorPosition
        processText()

        emitState()
    }

    fun onChangeHeightText(text: String) {
        error = null
        this.height = text

        emitState()
    }

    fun onDatePicked(date: LocalDate) {
        error = null
        val pickedHeight = validateMoneroHeightUseCase.getHeight(date)
        if (pickedHeight == -1L) {
            errorHeight = Translator.getString(Res.string.invalid_height_format)
        } else {
            height = pickedHeight.toString()
            errorHeight = null
        }

        emitState()
    }

    fun setMnemonicLanguage(language: Language) {
        if (isMoneroMnemonic) {
            return
        }
        setNormalMnemonicLanguage(language)
        processText()

        emitState()
    }

    fun onProceed() = viewModelScope.launch {
        val wordItems = phraseInput.wordItems
        when {
            phraseInput.invalidWordItems.isNotEmpty() -> {
                phraseInput = phraseInput.copy(invalidWordRanges = phraseInput.invalidWordItems.map { it.range })
            }

            isMoneroMnemonic && wordItems.size != MoneroConfig.WORD_COUNT -> {
                error = Translator.getString(
                    Res.string.Restore_Error_MnemonicWordCount_monero,
                    wordItems.size
                )
            }

            isMoneroMnemonic && validateMoneroHeightUseCase(height) == -1L -> {
                errorHeight = Translator.getString(Res.string.invalid_height_format)
            }

            (!isMoneroMnemonic && wordItems.size !in (Mnemonic.EntropyStrength.entries.map { it.wordCount })) -> {
                error =
                    Translator.getString(Res.string.Restore_Error_MnemonicWordCount, wordItems.size)
            }

            passphraseEnabled && passphrase.isBlank() -> {
                passphraseError = Translator.getString(Res.string.Restore_Error_EmptyPassphrase)
            }

            else -> {
                try {
                    val words = wordItems.map { it.word.normalizeNFKD() }
                    validateMoneroMnemonicUseCase(words, isMoneroMnemonic)

                    accountType = if (isMoneroMnemonic) {
                        moneroWalletUseCase.restore(
                            words = words,
                            height = validateMoneroHeightUseCase(height)
                        )
                    } else {
                        AccountType.Mnemonic(words, passphrase.normalizeNFKD())
                    }
                    error = if (accountType == null) {
                        Translator.getString(Res.string.monero_restore_error)
                    } else {
                        null
                    }
                    errorHeight = null

                    if (accountType is AccountType.MnemonicMonero) {
                        finishRestoringMoneroAccount()
                    }
                } catch (_: Exception) {
                    error = Translator.getString(Res.string.Restore_InvalidChecksum)
                }
            }
        }

        emitState()
    }

    private suspend fun finishRestoringMoneroAccount() {
        val accountType = accountType ?: return

        val account = accountFactory.account(
            name = accountName,
            type = accountType,
            origin = AccountOrigin.Restored,
            backedUp = true,
            fileBackedUp = false,
        )

        accountManager.save(account)
        walletActivator.activateWalletsSuspended(
            account,
            listOf(TokenQuery(BlockchainType.Monero, TokenType.Native))
        )
    }

    fun onSelectCoinsShown() {
        accountType = null

        emitState()
    }

    fun handleScannedQrData(scannedText: String): RestoreMnemonicModule.QrScanResult {
        if (!scannedText.startsWith(SeedPhraseQrCrypto.QR_PREFIX)) {
            return scannedText.toShamirPrefillOrNull()?.let(RestoreMnemonicModule.QrScanResult::Shamir)
                ?: RestoreMnemonicModule.QrScanResult.PlainText(scannedText)
        }

        return seedPhraseQrCrypto.decrypt(scannedText).fold(
            onSuccess = { decrypted ->
                decrypted.toShamirPrefillOrNull()?.let(RestoreMnemonicModule.QrScanResult::Shamir)
                    ?: RestoreMnemonicModule.QrScanResult.Success(
                        words = decrypted.words,
                        passphrase = decrypted.passphrase,
                        moneroHeight = decrypted.height,
                        language = decrypted.language
                    )
            },
            onFailure = { error ->
                RestoreMnemonicModule.QrScanResult.Error(
                    error.toSeedQrErrorMessage()
                )
            }
        )
    }

    fun applyMnemonicPhrase(
        words: List<String>,
        passphrase: String,
        moneroHeight: Long?,
        language: Language?
    ) {
        val wordsText = words.joinToString(" ")

        val isMonero = words.size == 25 && moneroHeight != null
        if (isMonero) {
            isMoneroMnemonic = true
            height = moneroHeight.toString()
        } else {
            applyBip39LanguageHint(words, language)
        }

        if (passphrase.isNotEmpty()) {
            passphraseEnabled = true
            this.passphrase = passphrase
        }

        text = wordsText
        cursorPosition = wordsText.length
        processText()

        emitState()
    }

    private fun applyBip39LanguageHint(words: List<String>, language: Language?) {
        if (language != null) {
            setNormalMnemonicLanguage(language)
            return
        }
        Bip39LanguageDetector.detectExact(words).firstOrNull()
            ?.let(::setNormalMnemonicLanguage)
    }

    private fun setNormalMnemonicLanguage(language: Language) {
        this.language = language
        normalMnemonicWordList = WordList.wordListStrict(language)
    }

    private fun autodetectLanguage(words: List<String>) {
        val detected = Bip39LanguageDetector.detectExact(words)
        if (detected.isEmpty() || language in detected) return
        setNormalMnemonicLanguage(detected.first())
    }

    private val displayedLanguage: Language
        get() = if (isMoneroMnemonic) Language.English else language

    companion object {
        // Single-word input is too ambiguous (e.g. "ábaco" exists in Spanish only, but
        // "abandon" prefixes match many wordlists) — only autodetect on multi-word input.
        private const val MIN_WORDS_FOR_AUTODETECT = 2
    }
}
