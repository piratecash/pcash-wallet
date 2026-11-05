package cash.p.terminal.modules.createaccount

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.IAccountFactory
import cash.p.terminal.core.WalletOnboardingStorage
import cash.p.terminal.core.managers.RestoreSettings
import cash.p.terminal.core.managers.RestoreSettingsManager
import cash.p.terminal.core.managers.WalletActivator
import cash.p.terminal.core.managers.WordsManager
import cash.p.terminal.core.providers.PredefinedBlockchainSettingsProvider
import cash.p.terminal.core.usecase.MoneroWalletUseCase
import cash.p.terminal.modules.mnemonic.mnemonicLanguagesOrdered
import io.horizontalsystems.core.Translator
import cash.p.terminal.ui_compose.entities.DataState
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.PassphraseValidator
import cash.p.terminal.wallet.SeedAccountType
import cash.p.terminal.wallet.data.MnemonicKind
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.isCompatibleWith
import cash.p.terminal.wallet.normalizeNFKD
import cash.p.terminal.wallet.slip39.Slip39AccountTypeFactory
import cash.p.terminal.wallet.slip39.Slip39SecretSize
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.hdwalletkit.Language
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import co.touchlab.kermit.Logger
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.resources.CreateWallet_Error_EmptyPassphrase
import cash.p.terminal.resources.CreateWallet_Error_InvalidConfirmation
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.create_wallet_error_failed
import org.jetbrains.compose.resources.StringResource

/** What the create screen generates; BIP-39 lengths stay in [MnemonicKind], which also classifies restores. */
sealed interface WalletKind {
    data class Mnemonic(val kind: MnemonicKind) : WalletKind
    data class Shamir(val size: Slip39SecretSize) : WalletKind
}

class CreateAdvancedAccountViewModel(
    private val accountFactory: IAccountFactory,
    private val wordsManager: WordsManager,
    private val accountManager: IAccountManager,
    private val walletActivator: WalletActivator,
    private val passphraseValidator: PassphraseValidator,
    private val predefinedBlockchainSettingsProvider: PredefinedBlockchainSettingsProvider,
    private val restoreSettingsManager: RestoreSettingsManager,
    private val localStorage: WalletOnboardingStorage,
    private val slip39AccountTypeFactory: Slip39AccountTypeFactory,
) : ViewModel() {

    private var passphrase = ""
    private var passphraseConfirmation = ""

    val passphraseTermsAgreed: Boolean
        get() = localStorage.passphraseTermsAgreed

    var loading by mutableStateOf(false)
        private set

    val walletKinds: List<WalletKind> =
        MnemonicKind.entries.filter { it != MnemonicKind.Unknown }.map(WalletKind::Mnemonic) +
            Slip39SecretSize.entries.map(WalletKind::Shamir)
    val mnemonicLanguages = mnemonicLanguagesOrdered

    private val moneroWalletUseCase: MoneroWalletUseCase by lazy { getKoinInstance<MoneroWalletUseCase>() }

    val defaultAccountName = accountFactory.getNextAccountName()
    var accountName: String = defaultAccountName
        get() = field.ifBlank { defaultAccountName }
        private set

    var selectedKind by mutableStateOf<WalletKind>(WalletKind.Mnemonic(MnemonicKind.Mnemonic12))
        private set

    val isShamir: Boolean
        get() = selectedKind is WalletKind.Shamir

    var selectedLanguage by mutableStateOf(Language.English)
        private set

    val languageSelectionEnabled: Boolean
        get() = selectedKind.let { it is WalletKind.Mnemonic && it != MONERO_KIND }

    val displayedLanguage: Language
        get() = if (languageSelectionEnabled) selectedLanguage else Language.English

    val showPassphraseBlock: Boolean
        get() = selectedKind != MONERO_KIND

    var passphraseEnabled by mutableStateOf(false)
        private set

    var passphraseConfirmState by mutableStateOf<DataState.Error?>(null)
        private set

    var passphraseState by mutableStateOf<DataState.Error?>(null)
        private set

    var success by mutableStateOf<AccountType?>(null)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    fun createMnemonicAccount() = viewModelScope.launch {
        if (loading || (showPassphraseBlock && passphraseEnabled && passphraseIsInvalid())) {
            return@launch
        }

        loading = true

        val accountType = newAccountType(selectedKind)

        if (accountType == null) {
            loading = false
            return@launch
        }

        val account = accountFactory.account(
            name = accountName,
            type = accountType,
            origin = AccountOrigin.Created,
            backedUp = false,
            fileBackedUp = false,
        )

        accountManager.save(account)

        // Save birthday height for Monero-only accounts to RestoreSettings
        if (accountType is AccountType.MnemonicMonero) {
            val restoreSettings = RestoreSettings()
            restoreSettings.birthdayHeight = accountType.height
            restoreSettingsManager.save(restoreSettings, account, BlockchainType.Monero)
        }

        // Prepare birthdayHeight for blockchains that require it
        if (accountType is AccountType.Mnemonic) {
            predefinedBlockchainSettingsProvider.prepareNew(account, BlockchainType.Zcash)
        }
        if (accountType is SeedAccountType) {
            predefinedBlockchainSettingsProvider.prepareNew(account, BlockchainType.Monero)
        }

        activateDefaultWallets(account)
        loading = false
        success = accountType
    }

    fun onChangeAccountName(name: String) {
        accountName = name
    }

    fun onChangePassphrase(v: String) {
        passphrase = v
        passphraseState = passphraseValidator.forbiddenCharactersError(v, slip39 = isShamir)?.let(::errorState)
    }

    fun onChangePassphraseConfirmation(v: String) {
        passphraseConfirmState = null
        passphraseConfirmation = v
    }

    fun setKind(kind: WalletKind) {
        selectedKind = kind
        // Hiding the block drops its fields; a kept passphrase would be applied without being shown.
        if (!showPassphraseBlock) setPassphraseEnabledState(false)
    }

    fun setMnemonicLanguage(language: Language) {
        if (languageSelectionEnabled) {
            selectedLanguage = language
        }
    }

    fun shouldConfirmNonEnglishMnemonic(): Boolean {
        return languageSelectionEnabled && selectedLanguage != Language.English
    }

    fun setPassphraseEnabledState(enabled: Boolean) {
        passphraseEnabled = enabled
        if (!enabled) {
            passphrase = ""
            passphraseConfirmation = ""
        }
    }

    fun onSuccessMessageShown() {
        success = null
    }

    fun onErrorShown() {
        error = null
    }

    private fun passphraseIsInvalid(): Boolean {
        // Re-checked against the current kind: the text may have been typed under a looser character rule.
        val passphraseError = passphraseValidator.forbiddenCharactersError(passphrase, slip39 = isShamir)
            ?: Res.string.CreateWallet_Error_EmptyPassphrase.takeIf { passphrase.isBlank() }
        passphraseState = passphraseError?.let(::errorState)
        if (passphraseError != null) {
            return true
        }
        if (passphrase != passphraseConfirmation) {
            passphraseConfirmState = errorState(Res.string.CreateWallet_Error_InvalidConfirmation)
            return true
        }
        return false
    }

    private fun errorState(message: StringResource) = DataState.Error(Exception(Translator.getString(message)))

    private suspend fun activateDefaultWallets(account: Account) {
        val tokenQueries = if (account.type is AccountType.MnemonicMonero) {
            listOf(TokenQuery(BlockchainType.Monero, TokenType.Native))
        } else {
            getDefaultTokens(account.type)
        }
        walletActivator.activateWalletsSuspended(account, tokenQueries)
    }

    private fun getDefaultTokens(accountType: AccountType) = TokenQuery.defaultTokenQueries.filter {
        accountType.isCompatibleWith(it.blockchainType, it.tokenType)
    }

    private suspend fun newAccountType(kind: WalletKind): AccountType? {
        if (kind == MONERO_KIND) return moneroWalletUseCase.createNew()
        return try {
            when (kind) {
                is WalletKind.Shamir -> slip39AccountTypeFactory.createNew(kind.size, passphrase.normalizeNFKD())
                is WalletKind.Mnemonic -> mnemonicAccountType(kind.kind.wordsCount)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Logger.withTag("CreateAdvancedAccountViewModel").e(e) { "Failed to generate a new wallet" }
            error = Translator.getString(Res.string.create_wallet_error_failed)
            null
        }
    }

    private fun mnemonicAccountType(wordCount: Int): AccountType.Mnemonic {
        val words = wordsManager.generateWords(wordCount, selectedLanguage)
            .map { it.normalizeNFKD() }
        return AccountType.Mnemonic(words, passphrase.normalizeNFKD())
    }

    private companion object {
        val MONERO_KIND = WalletKind.Mnemonic(MnemonicKind.Mnemonic25)
    }
}
