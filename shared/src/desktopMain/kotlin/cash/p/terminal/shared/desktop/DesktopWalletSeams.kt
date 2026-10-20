package cash.p.terminal.shared.desktop

import cash.p.terminal.core.WalletOnboardingStorage
import cash.p.terminal.core.providers.AddressResolutionConfig
import cash.p.terminal.core.providers.ZcashDbMarker
import cash.p.terminal.core.storage.AppDatabaseProvider
import cash.p.terminal.core.usecase.MoneroWalletStorage
import cash.p.terminal.feature.wallet.PinStateInfo
import cash.p.terminal.network.data.EncodedSecrets
import cash.p.terminal.shared.desktop.vault.DesktopVaultFiles
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountDeletionPreflight
import cash.p.terminal.wallet.IAdapter
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.IBalanceAdapter
import cash.p.terminal.wallet.IDeletedWalletChecker
import cash.p.terminal.wallet.IHardwarePublicKeyStorage
import cash.p.terminal.wallet.IReceiveAdapter
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.BalanceData
import cash.p.terminal.wallet.entities.HardwarePublicKey
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import cash.p.terminal.wallet.policy.HardwareWalletTokenPolicy
import cash.p.terminal.wallet.useCases.IGetMoneroWalletFilesNameUseCase
import cash.p.terminal.wallet.useCases.IRemoveMoneroWalletFilesUseCase
import cash.p.terminal.wallet.useCases.MoneroWalletFiles
import io.horizontalsystems.core.DesktopSettingsStore
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.IThirdKeyboard
import io.horizontalsystems.core.entities.BlockchainType
import io.reactivex.Flowable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.security.SecureRandom

/** No chain adapters on desktop yet: no wallet has one, so premium resolves through the API, as on Android. */
class DesktopAdapterManager : IAdapterManager {
    override val adaptersReadyObservable: Flowable<Map<Wallet, IAdapter>> = Flowable.just(emptyMap())
    override val initializationInProgressFlow: StateFlow<Boolean> = MutableStateFlow(false)
    override val walletBalanceUpdatedFlow: SharedFlow<Wallet> = MutableSharedFlow()

    override fun startAdapterManager() = Unit
    override suspend fun refresh() = Unit
    override suspend fun <T> awaitAdapterForWallet(wallet: Wallet, timeoutMs: Long): T? = null
    override fun <T> getAdapterForWallet(wallet: Wallet): T? = null
    override fun getAdapterForWalletOld(wallet: Wallet): IAdapter? = null
    override fun <T> getAdapterForToken(token: Token): T? = null
    override fun getBalanceAdapterForWallet(wallet: Wallet): IBalanceAdapter? = null
    override fun getReceiveAdapterForWallet(wallet: Wallet): IReceiveAdapter? = null
    override fun refreshAdapters(wallets: List<Wallet>) = Unit
    override fun refreshByWallet(wallet: Wallet) = Unit
    override fun getAdjustedBalanceData(wallet: Wallet): BalanceData? = null
    override fun getAdjustedBalanceDataForToken(token: Token): BalanceData? = null
    override suspend fun getReceiveAddressForWallet(wallet: Wallet): String? = null
}

/** Desktop has no hardware wallets: no account ever reaches the hardware keys or token policy. */
object DesktopNoHardwareWallets : IHardwarePublicKeyStorage, HardwareWalletTokenPolicy {
    override fun deleteAll() = Unit
    override suspend fun save(keys: List<HardwarePublicKey>) = Unit
    override suspend fun getKey(
        accountId: String,
        blockchainType: BlockchainType,
        tokenType: TokenType,
    ): HardwarePublicKey? = null

    override suspend fun getKeyByBlockchain(accountId: String, blockchainType: BlockchainType): HardwarePublicKey? =
        null

    override suspend fun getAllPublicKeys(accountId: String): List<HardwarePublicKey> = emptyList()
    override fun isSupported(blockchainType: BlockchainType, tokenType: TokenType) = false
}

/** Readable before the vault exists (the intro needs it) and cleared by the wipe with the other settings. */
class DesktopWalletOnboardingStorage(private val settingsStore: DesktopSettingsStore) : WalletOnboardingStorage {
    override var termsAccepted: Boolean
        get() = settingsStore.flag(TERMS_ACCEPTED) ?: false
        set(value) = settingsStore.setFlag(TERMS_ACCEPTED, value)

    override var mainShowedOnce: Boolean
        get() = settingsStore.flag(MAIN_SHOWED_ONCE) ?: false
        set(value) = settingsStore.setFlag(MAIN_SHOWED_ONCE, value)

    override var passphraseTermsAgreed: Boolean
        get() = settingsStore.flag(PASSPHRASE_TERMS_AGREED) ?: false
        set(value) = settingsStore.setFlag(PASSPHRASE_TERMS_AGREED, value)

    override var safetyRulesAgreed: Boolean
        get() = settingsStore.flag(SAFETY_RULES_AGREED) ?: false
        set(value) = settingsStore.setFlag(SAFETY_RULES_AGREED, value)

    private companion object {
        const val TERMS_ACCEPTED = "termsAccepted"
        const val MAIN_SHOWED_ONCE = "mainShowedOnce"
        const val PASSPHRASE_TERMS_AGREED = "passphraseTermsAgreed"
        const val SAFETY_RULES_AGREED = "safetyRulesAgreed"
    }
}

/**
 * Wallet files live under the kits directory, so the wipe erases them. The file password has no
 * KeyStore to live in: it is random and stored in the account, in the encrypted passphrase column.
 */
class DesktopMoneroWalletStorage(private val files: DesktopVaultFiles) : MoneroWalletStorage {
    private val secureRandom = SecureRandom()

    override fun walletsRoot(): File = File(files.kitsDirectory, MONERO_DIRECTORY).apply { mkdirs() }

    override fun newWalletPassword(): String =
        ByteArray(PASSWORD_BYTES).also(secureRandom::nextBytes).joinToString("") { "%02x".format(it) }

    private companion object {
        const val MONERO_DIRECTORY = "monero"
        const val PASSWORD_BYTES = 32
    }
}

/** Desktop has a password, no PIN and no duress mode. */
object DesktopPinStateInfo : PinStateInfo {
    override val isPinSet = false
    override fun isDuressPinSet() = false
    override fun getDuressLevel() = 0
}

/** No Zcash sync runs on desktop yet, so there is no first sync to shorten. */
object DesktopZcashDbMarker : ZcashDbMarker {
    override fun markPristine(accountId: String) = Unit
}

/** Desktop has no build types: the release endpoint, as the Android release build. */
object DesktopAddressResolutionConfig : AddressResolutionConfig {
    override val udnApiKey: String get() = EncodedSecrets.UDN_API_KEY
    override val blocksDecodedEthereumRpc = "https://api.blocksdecoded.com/v1/ethereum-rpc/mainnet"
}

/** Reads the open session per call, so a wipe and a new vault never serve the old rows. */
class DesktopDeletedWalletChecker(private val databases: AppDatabaseProvider) : IDeletedWalletChecker {
    override suspend fun getDeletedTokenQueryIds(accountId: String): Set<String> =
        databases.database.userDeletedWalletDao().getDeletedTokenQueryIds(accountId).toSet()
}

/** Desktop has no balance hiding yet: everything is shown, only the auto-hide flag is kept for the backup. */
class DesktopBalanceHiddenManager(private val settingsStore: DesktopSettingsStore) : IBalanceHiddenManager {
    private val shown: StateFlow<Boolean> = MutableStateFlow(false)

    override val balanceHidden = false
    override val balanceAutoHidden get() = settingsStore.flag(BALANCE_AUTO_HIDDEN) ?: false
    override val balanceHiddenFlow = shown
    override val anyWalletVisibilityChangedFlow: Flow<Unit> = emptyFlow()
    override val anyTransactionVisibilityChangedFlow: Flow<Unit> = emptyFlow()

    override fun toggleBalanceHidden() = Unit
    override fun setBalanceHidden(hidden: Boolean) = Unit
    override fun setBalanceAutoHidden(enabled: Boolean) = settingsStore.setFlag(BALANCE_AUTO_HIDDEN, enabled)
    override fun walletBalanceHiddenFlow(walletUid: String) = shown
    override fun toggleWalletBalanceHidden(walletUid: String) = Unit
    override fun isWalletBalanceHidden(walletUid: String) = false
    override fun setWalletBalanceHidden(walletUid: String, hidden: Boolean) = Unit
    override fun transactionInfoHiddenFlow(transactionId: String) = shown
    override fun transactionInfoHiddenFlowForWallet(transactionId: String, walletUid: String) = shown
    override fun toggleTransactionInfoHidden(transactionId: String) = Unit
    override fun isTransactionInfoHidden(transactionId: String) = false
    override fun isTransactionInfoHiddenForWallet(transactionId: String, walletUid: String) = false
    override fun clearSessionState() = Unit

    private companion object {
        const val BALANCE_AUTO_HIDDEN = "balanceAutoHidden"
    }
}

/** A desktop keyboard is never a third-party IME, so there is nothing to warn about. */
object DesktopThirdKeyboard : IThirdKeyboard {
    override var isThirdPartyKeyboardAllowed: Boolean
        get() = true
        set(_) = Unit
}

/** Monero wallet files are the only kit files on desktop; the file password lives in the account row. */
class DesktopRemoveMoneroWalletFilesUseCase(
    private val storage: MoneroWalletStorage,
    private val getMoneroWalletFilesNameUseCase: IGetMoneroWalletFilesNameUseCase,
    private val dispatcherProvider: DispatcherProvider,
) : IRemoveMoneroWalletFilesUseCase {
    override suspend fun invoke(account: Account): Boolean =
        getMoneroWalletFilesNameUseCase(account)?.let { invoke(it) } ?: false

    override suspend fun invoke(walletInnerName: String): Boolean = withContext(dispatcherProvider.io) {
        MoneroWalletFiles(File(storage.walletsRoot(), walletInnerName)).delete()
    }
}

/** No desktop kit keeps data that must outlive an account, so nothing ever blocks a deletion or reset. */
object DesktopAccountDeletionPreflight : AccountDeletionPreflight {
    override suspend fun ensureCanDelete(accountIds: List<String>) = Unit
    override suspend fun cleanupDeleted(accountIds: List<String>) = Unit
    override suspend fun prepareExplicitReset() = Unit
    override suspend fun resumePendingReset() = Unit
    override suspend fun ensureExplicitResetCleaned() = Unit
    override suspend fun finishExplicitReset() = Unit
    override fun ensureCanReset() = Unit
}
