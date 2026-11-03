package cash.p.terminal.shared.desktop

import androidx.compose.runtime.Composable
import cash.p.terminal.core.NonFatalReporter
import cash.p.terminal.core.UnsupportedAccountException
import cash.p.terminal.core.WalletOnboardingStorage
import cash.p.terminal.core.adapters.BitcoinDeviceSigner
import cash.p.terminal.core.adapters.BitcoinHardwareSigners
import cash.p.terminal.core.adapters.zcash.ZcashHardwareSigners
import cash.p.terminal.core.adapters.zcash.ZcashTransactionSigner
import cash.p.terminal.core.managers.BeamResetMarker
import cash.p.terminal.core.managers.EvmHardwareSigners
import cash.p.terminal.core.managers.MoneroHardwareGateway
import cash.p.terminal.core.managers.PlatformAccountDataCleaner
import cash.p.terminal.core.managers.SolanaHardwareSigners
import cash.p.terminal.core.managers.StellarHardwareSigners
import cash.p.terminal.core.managers.TonHardwareSigners
import cash.p.terminal.core.managers.TronHardwareSigners
import cash.p.terminal.core.providers.AddressResolutionConfig
import cash.p.terminal.core.storage.AppDatabaseProvider
import cash.p.terminal.core.usecase.MoneroWalletStorage
import cash.p.terminal.feature.wallet.PinStateInfo
import cash.p.terminal.modules.balance.BalancePlatformActions
import cash.p.terminal.modules.contacts.DeleteContactsPinSettings
import cash.p.terminal.modules.managewallets.HardwareWalletKeyProvisioner
import cash.p.terminal.modules.managewallets.ProvisionResult
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.network.data.EncodedSecrets
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.platform_not_supported_yet
import cash.p.terminal.shared.desktop.vault.DesktopVaultFiles
import cash.p.terminal.tangem.domain.model.AddressBytesWithPublicKey
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountDeletionBlockedException
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IDeletedWalletChecker
import cash.p.terminal.wallet.IHardwarePublicKeyStorage
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.HardwarePublicKey
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.policy.HardwareWalletTokenPolicy
import cash.p.terminal.wallet.useCases.IGetMoneroWalletFilesNameUseCase
import cash.p.terminal.wallet.useCases.IRemoveMoneroWalletFilesUseCase
import cash.p.terminal.wallet.useCases.MoneroWalletFiles
import cash.p.terminal.wallet.useCases.ScanToAddUseCase
import co.touchlab.kermit.Logger
import com.solana.core.Account as SolanaAccount
import com.solana.core.PublicKey
import com.tonapps.blockchain.ton.contract.HashSigner
import io.horizontalsystems.bitcoincore.transactions.builder.IInputSigner
import io.horizontalsystems.bitcoincore.transactions.builder.ISchnorrInputSigner
import io.horizontalsystems.core.DesktopSettingsStore
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.IThirdKeyboard
import io.horizontalsystems.core.Translator
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.ethereumkit.core.signer.Signer
import io.horizontalsystems.ethereumkit.models.Chain
import io.horizontalsystems.ethereumkit.models.RawTransaction
import io.horizontalsystems.ethereumkit.models.Signature
import io.horizontalsystems.stellarkit.Signer as StellarSigner
import io.horizontalsystems.tronkit.transaction.Signer as TronSigner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.SecureRandom

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
    override fun isSupported(account: Account, token: Token) = !account.isHardwareWalletAccount
}

object DesktopNoHardwareKeyProvisioner : HardwareWalletKeyProvisioner {
    override suspend fun provisionPublicKeys(account: Account, tokenQueries: List<TokenQuery>) =
        ProvisionResult.Failed(Translator.getString(Res.string.platform_not_supported_yet))
}

/** No device to scan on desktop, so a scan never adds a token. */
object DesktopNoHardwareScanToAdd : ScanToAddUseCase {
    override suspend fun addTokensByScan(blockchainsToDerive: List<TokenQuery>, cardId: String, accountId: String) =
        false
}

/** No scanner, dApp connections or store rating on desktop; the app content is not composed while locked. */
object DesktopBalancePlatformActions : BalancePlatformActions {
    override val appLocked: StateFlow<Boolean> = MutableStateFlow(false)

    override fun openConnectionLink(
        text: String,
        scope: CoroutineScope,
        onPairingResult: (paired: Boolean) -> Unit,
    ) = false

    override fun isOfflineTransaction(text: String) = false
    override fun onBalancePageActive() = Unit
    override fun onBalancePageInactive() = Unit
}

/** Unreachable: [DesktopPlatformChainSupport] keeps hardware accounts away from the kits. */
object DesktopNoHardwareSigners :
    EvmHardwareSigners,
    BitcoinHardwareSigners,
    SolanaHardwareSigners,
    TronHardwareSigners,
    StellarHardwareSigners,
    TonHardwareSigners,
    ZcashHardwareSigners,
    MoneroHardwareGateway {
    override suspend fun <T> execute(account: Account, block: (String) -> T): T =
        throw UnsupportedAccountException()

    override fun cardSigner(publicKey: HardwarePublicKey, key: AddressBytesWithPublicKey, chain: Chain): Signer =
        throw UnsupportedAccountException()

    override fun trezorSigner(
        device: AccountType.TrezorDevice,
        publicKey: HardwarePublicKey,
        key: AddressBytesWithPublicKey,
        chain: Chain,
    ): Signer = throw UnsupportedAccountException()

    override suspend fun signReconciled(
        signer: Signer,
        rawTransaction: RawTransaction,
    ): Pair<RawTransaction, Signature>? = null

    override fun cardEcdsaSigner(
        accountId: String,
        blockchainType: BlockchainType,
        tokenType: TokenType,
    ): IInputSigner = throw UnsupportedAccountException()

    override fun cardSchnorrSigner(
        accountId: String,
        blockchainType: BlockchainType,
        tokenType: TokenType,
    ): ISchnorrInputSigner = throw UnsupportedAccountException()

    override fun trezorSigner(
        accountId: String,
        blockchainType: BlockchainType,
        tokenType: TokenType,
        coin: String,
    ): BitcoinDeviceSigner = throw UnsupportedAccountException()

    override fun cardAccount(publicKey: PublicKey, hardwarePublicKey: HardwarePublicKey): SolanaAccount =
        throw UnsupportedAccountException()

    override fun trezorAccount(publicKey: PublicKey, hardwarePublicKey: HardwarePublicKey): SolanaAccount =
        throw UnsupportedAccountException()

    override fun cardSigner(hardwarePublicKey: HardwarePublicKey, expectedPublicKeyBytes: ByteArray): TronSigner =
        throw UnsupportedAccountException()

    override fun trezorSigner(expectedAddressBase58: String, derivationPath: String): TronSigner =
        throw UnsupportedAccountException()

    override fun cardSigner(hardwarePublicKey: HardwarePublicKey): StellarSigner =
        throw UnsupportedAccountException()

    override fun trezorSigner(publicKey: ByteArray, derivationPath: String, networkPassphrase: String): StellarSigner =
        throw UnsupportedAccountException()

    override fun cardHashSigner(hardwarePublicKey: HardwarePublicKey): HashSigner =
        throw UnsupportedAccountException()

    override fun trezorSigner(wallet: Wallet, device: AccountType.TrezorDevice): ZcashTransactionSigner =
        throw UnsupportedAccountException()
}

/** The SMS notification settings this would clear exist only on Android. */
object DesktopPlatformAccountDataCleaner : PlatformAccountDataCleaner {
    override fun clear(accountId: String) = Unit
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
    override fun validateCurrentLevel(pin: String) = false
}

object DesktopDeleteContactsPinSettings : DeleteContactsPinSettings {
    @Composable
    override fun Section(navigation: HSNavigation) = Unit
}

/** No crash reporting service on desktop: the report goes to the log. */
object DesktopNonFatalReporter : NonFatalReporter {
    private val logger = Logger.withTag("NonFatal")

    override fun report(error: Throwable, message: String) = logger.w(error) { message }
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

/** Outside the vault database, like the Android marker, and dropped by the wipe with the other settings. */
class DesktopBeamResetMarker(private val settingsStore: DesktopSettingsStore) : BeamResetMarker {
    override val isSet: Boolean get() = settingsStore.flag(RESET_PENDING) == true

    override fun set() = write(true)

    override fun clear() = write(false)

    private fun write(value: Boolean) = try {
        settingsStore.setFlag(RESET_PENDING, value)
    } catch (e: IOException) {
        throw AccountDeletionBlockedException(e)
    }

    private companion object {
        const val RESET_PENDING = "beamResetPending"
    }
}
