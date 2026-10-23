package cash.p.terminal.modules.backuplocal.fullbackup

import androidx.annotation.Keep
import cash.p.terminal.core.IAccountFactory
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.core.managers.CurationResult
import cash.p.terminal.core.managers.DeniableEncryptionManager
import cash.p.terminal.core.managers.EncryptDecryptManager
import cash.p.terminal.core.managers.RestoreSettings
import cash.p.terminal.core.managers.RestoreSettingsManager
import cash.p.terminal.core.managers.RestoreSettingType
import cash.p.terminal.core.managers.curatedEnabledWallets
import cash.p.terminal.core.managers.storedToken
import cash.p.terminal.core.decodeBase64Lenient
import cash.p.terminal.core.encodeBase64
import cash.p.terminal.core.restoreSettingTypes
import cash.p.terminal.core.toRawHexString
import cash.p.terminal.entities.LaunchPage
import cash.p.terminal.modules.backuplocal.BackupLocalModule
import cash.p.terminal.modules.contacts.model.Contact
import cash.p.terminal.modules.settings.appearance.PriceChangeInterval
import cash.p.terminal.modules.theme.ThemeType
import cash.p.terminal.resources.ImportBackupFile_Error_InvalidPassword
import cash.p.terminal.resources.Res
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IEnabledWalletStorage
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.favorites.MarketFavoritesManager
import cash.p.terminal.wallet.balance.BalanceViewType
import cash.p.terminal.wallet.entities.TokenQuery
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import io.horizontalsystems.core.Translator
import io.horizontalsystems.core.entities.BlockchainType
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

class BackupFileValidator {
    private val gson: Gson by lazy {
        GsonBuilder()
            .disableHtmlEscaping()
            .enableComplexMapKeySerialization()
            .create()
    }

    /**
     * Validates JSON-based backup file (V3 and legacy formats).
     * For binary V4 format, use validateBinary() instead.
     */
    fun validate(json: String) {
        // Check for V3 format - no validation needed, will be validated after decryption
        val backupV3 = try {
            gson.fromJson(json, BackupLocalModule.BackupV3::class.java)
        } catch (e: Exception) {
            null
        }
        if (backupV3?.version == BackupLocalModule.BACKUP_VERSION && backupV3.encrypted.isNotEmpty()) {
            return
        }

        // Legacy format validation
        val fullBackup = gson.fromJson(json, FullBackup::class.java)
        val walletBackup = gson.fromJson(json, BackupLocalModule.WalletBackup::class.java)

        @Suppress("SENSELESS_COMPARISON")
        val isSingleWalletBackup =
            fullBackup.settings == null &&
                    walletBackup.crypto != null &&
                    walletBackup.type != null &&
                    walletBackup.version in 1..2

        @Suppress("SENSELESS_COMPARISON")
        val isFullBackup =
            fullBackup.settings != null &&
                    fullBackup.version == 2 &&
                    walletBackup.crypto == null &&
                    walletBackup.type == null

        if (!isSingleWalletBackup && !isFullBackup) {
            throw Exception("Invalid json format")
        }
    }

    /**
     * Validates V4 binary backup file.
     * @param data Binary file contents
     * @throws Exception if format is invalid
     */
    fun validateBinary(data: ByteArray) {
        if (!BackupLocalModule.BackupV4Binary.isBinaryFormat(data)) {
            throw Exception("Invalid binary format: missing magic bytes")
        }

        val version = BackupLocalModule.BackupV4Binary.getVersion(data)
        if (version != BackupLocalModule.BackupV4Binary.VERSION) {
            throw Exception("Unsupported binary version: $version")
        }

        val container = BackupLocalModule.BackupV4Binary.extractContainer(data)
        if (container == null || container.size < 50_000) { // MIN_CONTAINER_SIZE
            throw Exception("Invalid container size")
        }
    }
}


/** Wallets, watchlist and the file format; app settings and contacts come from [appSettingsBackup]. */
open class WalletBackupProvider(
    private val walletStorage: IEnabledWalletStorage,
    private val settingsManager: RestoreSettingsManager,
    private val accountManager: IAccountManager,
    private val accountFactory: IAccountFactory,
    private val walletManager: IWalletManager,
    private val restoreSettingsManager: RestoreSettingsManager,
    private val marketFavoritesManager: MarketFavoritesManager,
    private val appSettingsBackup: AppSettingsBackup,
) {
    private val version = 2
    private val versionV3 = BackupLocalModule.BACKUP_VERSION

    private val gson: Gson by lazy {
        GsonBuilder()
            .disableHtmlEscaping()
            .enableComplexMapKeySerialization()
            .create()
    }

    /**
     * Derives the encryption key from passphrase using Scrypt KDF.
     * This is expensive (~1 second), so the result should be cached and reused.
     */
    fun deriveKey(
        passphrase: String,
        kdfParams: BackupLocalModule.KdfParams = BackupLocalModule.kdfDefault
    ): ByteArray {
        val key = EncryptDecryptManager.getKey(passphrase, kdfParams)
            ?: throw RestoreException.EncryptionKeyException
        return key
    }

    @Throws
    suspend fun accountType(
        backup: BackupLocalModule.WalletBackup,
        passphrase: String
    ): AccountType? {
        val decrypted = decryptedBackupCrypto(backup.crypto, passphrase)
        return BackupLocalModule.getAccountTypeFromData(backup.type, decrypted)
    }

    /**
     * Decrypts wallet backup using a pre-derived key (avoids expensive Scrypt call).
     * Only uses cached key if wallet's kdfparams match; otherwise derives a new key.
     */
    @Throws
    suspend fun accountTypeWithKey(
        backup: BackupLocalModule.WalletBackup,
        cachedKey: ByteArray,
        cachedKdfParams: BackupLocalModule.KdfParams,
        passphrase: String
    ): AccountType? {
        val walletKdfParams = backup.crypto.kdfparams
        val decrypted = if (kdfParamsMatch(cachedKdfParams, walletKdfParams)) {
            decryptedBackupCryptoWithKey(backup.crypto, cachedKey)
        } else {
            // KDF params differ, derive key specifically for this wallet
            decryptedBackupCrypto(backup.crypto, passphrase)
        }
        return BackupLocalModule.getAccountTypeFromData(backup.type, decrypted)
    }

    /**
     * Checks if two KdfParams are equivalent (same derivation would produce same key).
     */
    private fun kdfParamsMatch(
        a: BackupLocalModule.KdfParams,
        b: BackupLocalModule.KdfParams
    ): Boolean {
        return a.dklen == b.dklen &&
                a.n == b.n &&
                a.p == b.p &&
                a.r == b.r &&
                a.salt == b.salt
    }

    /**
     * [source] decides only whether a manually-added row's stored decimals may be trusted — never whether the user
     * is asked.
     */
    private suspend fun trustedEnabledWallets(
        accountId: String,
        backups: List<BackupLocalModule.EnabledWalletBackup>,
        source: BackupSource,
        approved: Set<String>
    ): CurationResult = getKoinInstance<MarketKitWrapper>()
        .curatedEnabledWallets(
            accountId,
            backups.map {
                storedToken(
                    tokenQueryId = it.tokenQueryId,
                    trustedDecimals = source == BackupSource.Authenticated,
                    coinName = it.coinName,
                    coinCode = it.coinCode,
                    decimals = it.decimals
                )
            },
            approvedTokenQueryIds = approved
        )

    /**
     * Restores single wallet from V4 backup using pre-decrypted WalletBackupItem.
     * [approved] `null` blocks the write instead, returning [RestoreOutcome.TokensNeedReview].
     */
    suspend fun restoreSingleWalletBackup(
        walletBackupItem: WalletBackupItem,
        approved: Set<String>? = null,
    ): RestoreOutcome = restoreWallets(
        listOf(walletBackupItem),
        approved?.let { mapOf(walletBackupItem.account.id to it) },
    )

    /** [source] is the envelope [backup] came from (raw v1/v2 vs. decrypted V3), not the backup itself. */
    suspend fun restoreSingleWalletBackup(
        type: AccountType,
        accountName: String,
        backup: BackupLocalModule.WalletBackup,
        source: BackupSource,
        approved: Set<String>? = null,
    ): RestoreOutcome {
        val account = accountFactory.account(
            accountName,
            type,
            AccountOrigin.Restored,
            backup.manualBackup,
            true
        )

        // Resolved before the account is persisted so a failing lookup can't leave an orphan account.
        val enabledWalletBackups = backup.enabledWallets ?: listOf()
        val curation = trustedEnabledWallets(account.id, enabledWalletBackups, source, approved ?: emptySet())

        if (approved == null && curation.declined.isNotEmpty()) {
            return RestoreOutcome.TokensNeedReview(
                listOf(WalletDeclinedTokens(account.id, accountName, curation.declined))
            )
        }

        accountManager.save(account)
        walletManager.saveEnabledWallets(curation.enabled)

        enabledWalletBackups.forEach { enabledWalletBackup ->
            TokenQuery.fromId(enabledWalletBackup.tokenQueryId)?.let { tokenQuery ->
                val restoreSettings = enabledWalletBackup.restoreSettings(tokenQuery)
                if (restoreSettings != null) {
                    restoreSettingsManager.save(restoreSettings, account, tokenQuery.blockchainType)
                } else if (type is AccountType.MnemonicMonero) {
                    // For Monero-only accounts without settings in backup, save height from AccountType
                    val restoreSettings = RestoreSettings()
                    restoreSettings.birthdayHeight = type.height
                    restoreSettingsManager.save(restoreSettings, account, tokenQuery.blockchainType)
                }
            }
        }

        return RestoreOutcome.Restored
    }

    /** No side effects — kept separate from writing so the caller can decide whether to write at all. */
    private suspend fun curateWalletBackups(
        walletBackupItems: List<WalletBackupItem>,
        approved: Map<String, Set<String>>?,
    ): List<Pair<WalletBackupItem, CurationResult>> = walletBackupItems.map { item ->
        item to trustedEnabledWallets(
            item.account.id,
            item.enabledWallets,
            item.source,
            approved?.get(item.account.id) ?: emptySet()
        )
    }

    /** Null when [approved] was supplied, or nothing in [curated] was declined. */
    private fun reviewOutcomeOrNull(
        curated: List<Pair<WalletBackupItem, CurationResult>>,
        approved: Map<String, Set<String>>?,
    ): RestoreOutcome.TokensNeedReview? {
        if (approved != null) return null
        val declinedWallets = curated.mapNotNull { (item, result) ->
            result.declined.takeIf { it.isNotEmpty() }
                ?.let { WalletDeclinedTokens(item.account.id, item.account.name, it) }
        }
        return declinedWallets.takeIf { it.isNotEmpty() }?.let(RestoreOutcome::TokensNeedReview)
    }

    private suspend fun writeCuratedWallets(curated: List<Pair<WalletBackupItem, CurationResult>>) {
        if (curated.isEmpty()) return
        curated.forEach { (item, _) -> restoreWalletSettings(item) }
        accountManager.import(curated.map { it.first.account })
        walletManager.saveEnabledWallets(curated.flatMap { it.second.enabled })
    }

    private suspend fun restoreWallets(
        walletBackupItems: List<WalletBackupItem>,
        approved: Map<String, Set<String>>?,
    ): RestoreOutcome {
        if (walletBackupItems.isEmpty()) return RestoreOutcome.Restored

        val curated = curateWalletBackups(walletBackupItems, approved)
        reviewOutcomeOrNull(curated, approved)?.let { return it }

        writeCuratedWallets(curated)
        return RestoreOutcome.Restored
    }

    /** Saved even for rows [trustedEnabledWallets] dropped, so a birthday height isn't lost. */
    private fun restoreWalletSettings(walletBackupItem: WalletBackupItem) {
        val account = walletBackupItem.account
        walletBackupItem.enabledWallets.forEach { enabledWalletBackup ->
            val tokenQuery = TokenQuery.fromId(enabledWalletBackup.tokenQueryId) ?: return@forEach
            val accountType = account.type
            val restoreSettings = enabledWalletBackup.restoreSettings(tokenQuery)
                ?: if (
                    accountType is AccountType.MnemonicMonero &&
                    tokenQuery.blockchainType == BlockchainType.Monero
                ) {
                    // Monero-only accounts carry no settings in the backup — take the height from the account
                    RestoreSettings().apply { birthdayHeight = accountType.height }
                } else {
                    return@forEach
                }
            restoreSettingsManager.save(restoreSettings, account, tokenQuery.blockchainType)
        }
    }

    /**
     * All accounts are curated before anything is written, so a late decline can't leave earlier accounts partially
     * written.
     */
    @Throws
    suspend fun restoreFullBackup(
        fullBackup: DecryptedFullBackup,
        passphrase: String,
        approved: Map<String, Set<String>>? = null,
    ): RestoreOutcome {
        val curatedWallets = curateWalletBackups(fullBackup.wallets, approved)
        reviewOutcomeOrNull(curatedWallets, approved)?.let { return it }

        writeCuratedWallets(curatedWallets)

        if (fullBackup.watchlist.isNotEmpty()) {
            marketFavoritesManager.addAll(fullBackup.watchlist)
        }

        fullBackup.settings?.let { appSettingsBackup.restore(it, passphrase) }

        if (fullBackup.contacts.isNotEmpty()) {
            appSettingsBackup.restoreContacts(fullBackup.contacts)
        }

        return RestoreOutcome.Restored
    }

    suspend fun decryptedFullBackup(
        fullBackup: FullBackup,
        passphrase: String,
        source: BackupSource
    ): DecryptedFullBackup {
        return decryptedFullBackupWithKey(fullBackup, null, null, passphrase, source)
    }

    /**
     * Decrypts full backup using a pre-derived key (avoids expensive Scrypt calls for each wallet).
     * If cachedKey is null, falls back to deriving key from passphrase for each wallet.
     * The cachedKdfParams are used to verify the cached key is valid for each inner wallet's encryption.
     */
    suspend fun decryptedFullBackupWithKey(
        fullBackup: FullBackup,
        cachedKey: ByteArray?,
        cachedKdfParams: BackupLocalModule.KdfParams?,
        passphrase: String,
        source: BackupSource
    ): DecryptedFullBackup {
        val walletBackupItems = mutableListOf<WalletBackupItem>()
        val usedNames = mutableSetOf<String>()

        fullBackup.wallets?.forEach { walletBackup2 ->
            val backup = walletBackup2.backup
            val type = if (cachedKey != null && cachedKdfParams != null) {
                accountTypeWithKey(backup, cachedKey, cachedKdfParams, passphrase)
            } else {
                accountType(backup, passphrase)
            }
            if (type == null) { // AccountType not supported for restoration
                return@forEach
            }

            // Skip hardware wallets - they cannot be restored from backup
            // (private keys are stored on hardware device, not in backup)
            if (type is AccountType.HardwareCard || type is AccountType.HardwareDevice) {
                return@forEach
            }

            val name = accountFactory.getUniqueName(walletBackup2.name, usedNames)
            usedNames.add(name)

            val account = if (type.isWatchAccountType) {
                accountFactory.watchAccount(name, type)
            } else {
                accountFactory.account(
                    name,
                    type,
                    AccountOrigin.Restored,
                    backup.manualBackup,
                    true // Restoring from file proves file backup exists
                )
            }

            walletBackupItems.add(
                WalletBackupItem(
                    account = account,
                    enabledWallets = backup.enabledWallets ?: listOf(),
                    source = source
                )
            )
        }

        var contacts = listOf<Contact>()
        fullBackup.contacts?.let { contactsCrypto ->
            val decrypted = if (cachedKey != null && cachedKdfParams != null) {
                // Check if cached key can be used for contacts decryption
                if (kdfParamsMatch(cachedKdfParams, contactsCrypto.kdfparams)) {
                    decryptedBackupCryptoWithKey(contactsCrypto, cachedKey)
                } else {
                    decryptedBackupCrypto(contactsCrypto, passphrase)
                }
            } else {
                decryptedBackupCrypto(contactsCrypto, passphrase)
            }
            val contactsBackupJson = String(decrypted, Charsets.UTF_8)

            contacts = appSettingsBackup.parseContacts(contactsBackupJson)
        }

        return DecryptedFullBackup(
            wallets = walletBackupItems,
            watchlist = fullBackup.watchlist ?: listOf(),
            settings = fullBackup.settings,
            contacts = contacts
        )
    }

    private fun fullBackupItems(
        accounts: List<Account>,
        watchlist: List<String>,
        contacts: List<Contact>,
        customRpcsCount: Int?
    ): BackupItems {
        // Filter out hardware wallets - they cannot be backed up (private keys are on hardware)
        val nonWatchAccounts = accounts.filter { !it.isHardwareWalletAccount && !it.isWatchAccount }
            .sortedBy { it.name.lowercase() }
        val watchAccounts = accounts.filter { !it.isHardwareWalletAccount && it.isWatchAccount }
        return BackupItems(
            accounts = nonWatchAccounts,
            watchWallets = watchAccounts.ifEmpty { null }?.size,
            watchlist = watchlist.ifEmpty { null }?.size,
            contacts = contacts.ifEmpty { null }?.size,
            customRpc = customRpcsCount,
        )
    }

    suspend fun fullBackupItems() =
        fullBackupItems(
            accounts = accountManager.accounts,
            watchlist = marketFavoritesManager.getAll(),
            contacts = appSettingsBackup.contacts,
            customRpcsCount = appSettingsBackup.customRpcCount()
        )

    fun fullBackupItems(decryptedFullBackup: DecryptedFullBackup) =
        fullBackupItems(
            accounts = decryptedFullBackup.wallets.map { it.account },
            watchlist = decryptedFullBackup.watchlist,
            contacts = decryptedFullBackup.contacts,
            customRpcsCount = decryptedFullBackup.settings?.evmSyncSources?.custom?.ifEmpty { null }?.size
        )

    fun shouldShowReplaceWarning(decryptedFullBackup: DecryptedFullBackup?): Boolean {
        return decryptedFullBackup != null &&
                decryptedFullBackup.contacts.isNotEmpty() &&
                appSettingsBackup.contacts.isNotEmpty()
    }

    @Throws
    fun createWalletBackup(account: Account, passphrase: String): String {
        // Hardware wallets cannot be backed up - private keys are on hardware device
        if (account.isHardwareWalletAccount) {
            throw IllegalArgumentException("Hardware wallets cannot be backed up")
        }
        val backup = walletBackup(account, passphrase)
        val innerJson = gson.toJson(backup)
        return wrapInV3Format(innerJson, passphrase)
    }

    @Throws
    suspend fun createFullBackup(accountIds: List<String>, passphrase: String): String {
        // Derive key once and reuse for all encryptions
        val cachedKey = deriveBackupKey(passphrase)

        val wallets = accountManager.accounts
            // Exclude hardware wallets - private keys are on hardware, cannot backup
            .filter { !it.isHardwareWalletAccount }
            .filter { it.isWatchAccount || accountIds.contains(it.id) }
            .mapNotNull {
                val accountBackup = walletBackupWithKey(it, cachedKey, passphrase) ?: return@mapNotNull null
                WalletBackup2(it.name, accountBackup)
            }

        val watchlist = marketFavoritesManager.getAll()

        val settings = appSettingsBackup.currentSettings(cachedKey, passphrase)

        val contacts = encryptedContacts(cachedKey, passphrase)

        val fullBackup = FullBackup(
            wallets = wallets.ifEmpty { null },
            watchlist = watchlist.ifEmpty { null },
            settings = settings,
            contacts = contacts,
            timestamp = System.currentTimeMillis() / 1000,
            version = version,
            id = UUID.randomUUID().toString()
        )

        val innerJson = gson.toJson(fullBackup)
        return wrapInV3FormatWithKey(innerJson, cachedKey, passphrase)
    }

    /**
     * Creates V4 deniable encryption backup in binary format.
     * 50% smaller than JSON+Hex format.
     *
     * File structure: [4B magic "PW4B"][1B version][container bytes]
     *
     * Automatically retries with new salt (up to 10 times) if password-derived
     * offsets collide. Each salt produces different offsets, so collisions
     * are resolved by regenerating the salt.
     *
     * @param accountIds1 Primary wallet IDs (can be revealed under duress)
     * @param passphrase1 Primary password
     * @param accountIds2 Hidden wallet IDs (optional, deniable)
     * @param passphrase2 Hidden password (required if accountIds2 provided)
     * @return Binary backup file contents
     * @throws DeniableEncryptionManager.PasswordCollisionException if passwords derive overlapping offsets after all
     * retries
     */
    @Throws(DeniableEncryptionManager.PasswordCollisionException::class)
    suspend fun createFullBackupV4Binary(
        accountIds1: List<String>,
        passphrase1: String,
        accountIds2: List<String>?,
        passphrase2: String?
    ): ByteArray {
        val payload1 = buildV4Payload(
            buildFullBackupJson(accountIds1, passphrase1, includeWatchAccounts = true)
        )

        val payload2 = if (accountIds2 != null && passphrase2 != null) {
            buildV4Payload(
                buildFullBackupJson(accountIds2, passphrase2, includeWatchAccounts = false)
            )
        } else null

        return createDeniableContainerWithRetry(payload1, passphrase1, payload2, passphrase2)
    }

    /**
     * Creates V4 binary backup for a single wallet.
     * Uses the same binary format as full backup for consistency.
     * Single wallet = one password, no deniability.
     *
     * @param account The wallet account to backup
     * @param passphrase Password for encryption
     * @return Binary backup file contents
     */
    fun createSingleWalletBackupV4Binary(
        account: Account,
        passphrase: String
    ): ByteArray {
        if (account.isHardwareWalletAccount) {
            throw IllegalArgumentException("Hardware wallets cannot be backed up")
        }

        val backup = buildSingleWalletFullBackup(account, passphrase) ?: throw IllegalArgumentException(
            "Failed to build wallet backup"
        )
        val payload = buildV4Payload(backup)

        return createDeniableContainerWithRetry(payload, passphrase, null, null)
    }

    /**
     * Serializes FullBackup to padded JSON bytes for V4 container.
     */
    private fun buildV4Payload(fullBackup: FullBackup): ByteArray {
        val json = gson.toJson(fullBackup)
        val padded = addAlignmentPadding(json, TARGET_INNER_JSON_SIZE_V4)
        return padded.toByteArray(Charsets.UTF_8)
    }

    /**
     * Creates deniable container with automatic retry on offset collision.
     */
    private fun createDeniableContainerWithRetry(
        payload1: ByteArray,
        passphrase1: String,
        payload2: ByteArray?,
        passphrase2: String?
    ): ByteArray {
        val containerBytes = DeniableEncryptionManager.createContainerBytes(
            message1 = payload1,
            password1 = passphrase1,
            message2 = payload2,
            password2 = passphrase2
        )
        return BackupLocalModule.BackupV4Binary.create(containerBytes)
    }

    /**
     * Builds FullBackup containing a single wallet.
     * No settings stored - matches V3 behavior (only wallet data).
     */
    private fun buildSingleWalletFullBackup(account: Account, passphrase: String): FullBackup? {
        val cachedKey = deriveBackupKey(passphrase)
        val walletBackup = walletBackupWithKey(account, cachedKey, passphrase) ?: return null

        return FullBackup(
            wallets = listOf(WalletBackup2(account.name, walletBackup)),
            watchlist = null,
            settings = null,
            contacts = null,
            timestamp = System.currentTimeMillis() / 1000,
            version = version,
            id = UUID.randomUUID().toString()
        )
    }

    /**
     * Builds FullBackup object for given account IDs.
     * Used internally by createFullBackupV4Binary to create separate backups for each password.
     */
    private suspend fun buildFullBackupJson(
        accountIds: List<String>,
        passphrase: String,
        includeWatchAccounts: Boolean
    ): FullBackup {
        val cachedKey = deriveBackupKey(passphrase)

        val wallets = accountManager.accounts
            .filter { !it.isHardwareWalletAccount }
            .filter { (includeWatchAccounts && it.isWatchAccount) || accountIds.contains(it.id) }
            .mapNotNull {
                val accountBackup = walletBackupWithKey(it, cachedKey, passphrase) ?: return@mapNotNull null
                WalletBackup2(it.name, accountBackup)
            }

        val watchlist = marketFavoritesManager.getAll()

        val contacts = encryptedContacts(cachedKey, passphrase)

        return FullBackup(
            wallets = wallets.ifEmpty { null },
            watchlist = watchlist.ifEmpty { null },
            settings = appSettingsBackup.currentSettings(cachedKey, passphrase),
            contacts = contacts,
            timestamp = System.currentTimeMillis() / 1000,
            version = version,
            id = UUID.randomUUID().toString()
        )
    }

    /**
     * Restores from V4 binary backup using provided password.
     * Returns only the data encrypted with this specific password.
     *
     * @param binaryData The binary backup file contents
     * @param passphrase Password to decrypt with
     * @return DecryptedFullBackup if password is correct, null otherwise
     */
    suspend fun restoreFromV4BinaryBackup(
        binaryData: ByteArray,
        passphrase: String
    ): DecryptedFullBackup? {
        // Extract container from binary format
        val container = BackupLocalModule.BackupV4Binary.extractContainer(binaryData)
            ?: return null

        // Extract message from deniable container
        val decryptedBytes =
            DeniableEncryptionManager.extractMessageFromBytes(container, passphrase)
                ?: return null

        val innerJson = String(decryptedBytes, Charsets.UTF_8)

        // Parse as FullBackup
        val fullBackup = try {
            val backup = gson.fromJson(innerJson, FullBackup::class.java)
            backup.version // Verify it's a valid FullBackup
            backup
        } catch (e: Exception) {
            return null
        }

        // Inner wallet decryption uses kdfParams stored in each wallet's crypto field.
        // The GCM tag verified above covers enabled_wallets too, hence Authenticated.
        return decryptedFullBackup(fullBackup, passphrase, BackupSource.Authenticated)
    }

    /**
     * Checks if binary data is a V4 binary backup.
     */
    fun isV4BinaryBackup(data: ByteArray): Boolean {
        return BackupLocalModule.BackupV4Binary.isBinaryFormat(data)
    }

    private fun wrapInV3Format(innerJson: String, passphrase: String): String {
        return wrapInV3FormatWithKey(innerJson, null, passphrase)
    }

    private fun wrapInV3FormatWithKey(
        innerJson: String,
        cachedKey: ByteArray?,
        passphrase: String
    ): String {
        // Add padding to reach uniform ~100KB file size for privacy
        val alignedInnerJson = addAlignmentPadding(innerJson)

        val crypto = encryptedBackupCrypto(alignedInnerJson.toByteArray(Charsets.UTF_8), cachedKey, passphrase)
        val cryptoJson = gson.toJson(crypto)
        val base64Encoded = cryptoJson.toByteArray(Charsets.UTF_8).encodeBase64()

        val backupV3 = BackupLocalModule.BackupV3(
            version = versionV3,
            encrypted = base64Encoded
        )
        return gson.toJson(backupV3)
    }

    companion object {
        // Target size for inner JSON before encryption to achieve ~100KB final file
        // Measured expansion ratio: ~1.78x (innerJson → final file)
        // 56KB inner → ~100KB final
        private const val TARGET_INNER_JSON_SIZE = 56_000

        // Target size for V4 inner JSON before encryption
        // V4 container is ~100KB with raw data region
        // Each payload can be up to ~40KB to leave room for two payloads with overhead
        private const val TARGET_INNER_JSON_SIZE_V4 = 40_000

        // Characters safe for JSON string values (alphanumeric)
        private const val PADDING_CHARS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        private val PADDING_RANDOM = SecureRandom()
    }

    /**
     * Adds random padding to JSON to reach uniform file size (~100KB).
     * This prevents attackers from determining wallet count based on file size.
     * The padding is added as "align_payload" field which is ignored during deserialization.
     */
    private fun addAlignmentPadding(
        json: String,
        targetSize: Int = TARGET_INNER_JSON_SIZE
    ): String {
        val currentSize = json.length

        // If already at or above target, return as-is
        if (currentSize >= targetSize) {
            return json
        }

        // Calculate padding needed
        // Account for wrapper: ,"align_payload":"" (20 chars overhead)
        val wrapperOverhead = 20
        val paddingLength = targetSize - currentSize - wrapperOverhead

        if (paddingLength <= 0) {
            return json
        }

        // Generate cryptographically random padding
        val padding = generateRandomPadding(paddingLength)

        // Insert before closing brace: {...,"align_payload":"xxxxx"}
        val insertPosition = json.lastIndexOf('}')
        if (insertPosition == -1) {
            return json // Invalid JSON, return as-is
        }

        return json.substring(0, insertPosition) +
                ",\"align_payload\":\"$padding\"" +
                json.substring(insertPosition)
    }

    /**
     * Padding hides small wallet-count differences in encrypted backups, so its filler must not be predictable.
     */
    private fun generateRandomPadding(length: Int): String {
        val sb = StringBuilder(length)
        repeat(length) {
            sb.append(PADDING_CHARS[PADDING_RANDOM.nextInt(PADDING_CHARS.length)])
        }
        return sb.toString()
    }

    /**
     * Unwraps V3 format and returns the inner JSON along with cached key info.
     * The cached key can be reused for decrypting inner wallet backups if their kdfparams match.
     */
    fun unwrapV3FormatWithKey(
        backupV3: BackupLocalModule.BackupV3,
        passphrase: String
    ): Triple<String, ByteArray, BackupLocalModule.KdfParams> {
        val cryptoJsonBytes = backupV3.encrypted.decodeBase64Lenient()
        val cryptoJson = String(cryptoJsonBytes, Charsets.UTF_8)
        val crypto = gson.fromJson(cryptoJson, BackupLocalModule.BackupCrypto::class.java)

        // Derive key once and return it with kdfparams for reuse
        val kdfParams = crypto.kdfparams
        val key = deriveKey(passphrase, kdfParams)
        val decryptedBytes = decryptedBackupCryptoWithKey(crypto, key)
        return Triple(String(decryptedBytes, Charsets.UTF_8), key, kdfParams)
    }

    fun parseV3Backup(json: String): BackupLocalModule.BackupV3? {
        return try {
            val backupV3 = gson.fromJson(json, BackupLocalModule.BackupV3::class.java)
            if (backupV3.version == versionV3 && backupV3.encrypted.isNotEmpty()) backupV3 else null
        } catch (e: Exception) {
            null
        }
    }

    private fun getId(value: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-512")
        val digest = md.digest(value)
        return digest.toRawHexString()
    }

    private fun encryptedContacts(cachedKey: ByteArray, passphrase: String): BackupLocalModule.BackupCrypto? {
        if (appSettingsBackup.contacts.isEmpty()) return null
        val contactsJson = appSettingsBackup.contactsJson.toByteArray(Charsets.UTF_8)
        return encryptedBackupCrypto(contactsJson, cachedKey, passphrase)
    }

    /**
     * Derives encryption key from passphrase. Should be called once and reused.
     */
    private fun deriveBackupKey(passphrase: String): ByteArray {
        return EncryptDecryptManager.getKey(passphrase, BackupLocalModule.kdfDefault)
            ?: throw Exception("Couldn't get encryption key")
    }

    @Throws
    private fun walletBackup(account: Account, passphrase: String): BackupLocalModule.WalletBackup? {
        return walletBackupWithKey(account, null, passphrase)
    }

    /**
     * Creates wallet backup using a pre-derived key to avoid repeated Scrypt calls.
     */
    @Throws
    private fun walletBackupWithKey(
        account: Account,
        cachedKey: ByteArray?,
        passphrase: String
    ): BackupLocalModule.WalletBackup? {
        val secretText = BackupLocalModule.getDataForEncryption(account.type) ?: return null
        val id = getId(secretText)

        return BackupLocalModule.WalletBackup(
            crypto = encryptedBackupCrypto(secretText, cachedKey, passphrase),
            id = id,
            type = BackupLocalModule.getAccountTypeString(account.type),
            enabledWallets = enabledWalletBackups(account),
            manualBackup = account.isBackedUp,
            fileBackup = account.isFileBackedUp,
            timestamp = System.currentTimeMillis() / 1000,
            version = version
        )
    }

    fun enabledWalletBackups(account: Account): List<BackupLocalModule.EnabledWalletBackup> {
        return walletStorage.enabledWallets(account.id).mapNotNull {
            val tokenQuery = TokenQuery.fromId(it.tokenQueryId) ?: return@mapNotNull null
            BackupLocalModule.EnabledWalletBackup(
                tokenQueryId = it.tokenQueryId,
                coinName = it.coinName,
                coinCode = it.coinCode,
                decimals = it.coinDecimals,
                settings = settingsBackup(account, tokenQuery)
            )
        }
    }

    private fun BackupLocalModule.EnabledWalletBackup.restoreSettings(
        tokenQuery: TokenQuery
    ): RestoreSettings? {
        val allowedTypes = tokenQuery.restoreSettingTypes.toSet()
        if (allowedTypes.isEmpty()) return null

        val filteredSettings = settings
            ?.filterKeys { it in allowedTypes }
            .orEmpty()
        if (filteredSettings.isEmpty()) return null

        return RestoreSettings().apply {
            filteredSettings.forEach { (restoreSettingType, value) ->
                this[restoreSettingType] = value
            }
        }
    }

    private fun settingsBackup(
        account: Account,
        tokenQuery: TokenQuery
    ): Map<RestoreSettingType, String>? {
        val allowedTypes = tokenQuery.restoreSettingTypes.toSet()
        if (allowedTypes.isEmpty()) return null

        return settingsManager.settings(account, tokenQuery.blockchainType)
            .values
            .filterKeys { it in allowedTypes }
            .ifEmpty { null }
    }

}

fun decryptedBackupCrypto(crypto: BackupLocalModule.BackupCrypto, passphrase: String): ByteArray {
    val kdfParams = crypto.kdfparams
    val key = EncryptDecryptManager.getKey(passphrase, kdfParams)
        ?: throw RestoreException.EncryptionKeyException
    return decryptedBackupCryptoWithKey(crypto, key)
}

fun decryptedBackupCryptoWithKey(
    crypto: BackupLocalModule.BackupCrypto,
    key: ByteArray
): ByteArray {
    if (EncryptDecryptManager.passwordIsCorrect(crypto.mac, crypto.ciphertext, key)) {
        return EncryptDecryptManager().decrypt(crypto.ciphertext, key, crypto.cipherparams.iv)
    } else {
        throw RestoreException.InvalidPasswordException
    }
}

/**
 * Encrypts data using a pre-derived key to avoid repeated Scrypt calls.
 */
fun encryptedBackupCrypto(
    data: ByteArray,
    cachedKey: ByteArray?,
    passphrase: String
): BackupLocalModule.BackupCrypto {
    val kdfParams = BackupLocalModule.kdfDefault

    val key = cachedKey
        ?: (EncryptDecryptManager.getKey(passphrase, kdfParams)
            ?: throw Exception("Couldn't get encryption key"))

    val iv = EncryptDecryptManager.generateRandomBytes(16).toRawHexString()
    val encrypted = EncryptDecryptManager().encrypt(data, key, iv)
    val mac = EncryptDecryptManager.generateMac(key, encrypted.toByteArray())

    return BackupLocalModule.BackupCrypto(
        cipher = "aes-128-ctr",
        cipherparams = BackupLocalModule.CipherParams(iv),
        ciphertext = encrypted,
        kdf = "scrypt",
        kdfparams = kdfParams,
        mac = mac.toRawHexString()
    )
}

/**
 * [Authenticated] (V4's AES-GCM tag, or V3's MAC'd ciphertext) means `enabled_wallets` could not
 * be edited without the password. [Legacy] (raw v1/v2) sits outside any MAC and can be edited freely.
 */
enum class BackupSource { Authenticated, Legacy }

data class WalletBackupItem(
    val account: Account,
    val enabledWallets: List<BackupLocalModule.EnabledWalletBackup>,
    val source: BackupSource
)

data class DecryptedFullBackup(
    val wallets: List<WalletBackupItem>,
    val watchlist: List<String>,
    val settings: Settings?,
    val contacts: List<Contact>
)

sealed interface RestoreOutcome {
    data object Restored : RestoreOutcome

    /** Nothing was written: these rows need a decision first. */
    data class TokensNeedReview(val wallets: List<WalletDeclinedTokens>) : RestoreOutcome
}

data class BackupItems(
    val accounts: List<Account>,
    val watchWallets: Int?,
    val watchlist: Int?,
    val contacts: Int?,
    val customRpc: Int?,
)

@Keep
data class WalletBackup2(
    val name: String,
    val backup: BackupLocalModule.WalletBackup
)

@Keep
data class FullBackup(
    val wallets: List<WalletBackup2>?,
    val watchlist: List<String>?,
    val settings: Settings?,
    val contacts: BackupLocalModule.BackupCrypto?,
    val timestamp: Long,
    val version: Int,
    val id: String
)

@Keep
data class BtcMode(
    @SerializedName("blockchain_type_id")
    val blockchainTypeId: String,
    @SerializedName("restore_mode")
    val restoreMode: String,
    @SerializedName("sort_mode")
    val sortMode: String
)

@Keep
data class EvmSyncSourceBackup(
    @SerializedName("blockchain_type_id")
    val blockchainTypeId: String,
    val url: String,
    val auth: BackupLocalModule.BackupCrypto?
)

@Keep
data class EvmSyncSources(
    val selected: List<EvmSyncSourceBackup>,
    val custom: List<EvmSyncSourceBackup>
)

@Keep
data class SolanaSyncSource(
    @SerializedName("blockchain_type_id")
    val blockchainTypeId: String,
    val name: String
)

@Keep
data class RsiBackup(
    val period: Int,
    val enabled: Boolean
)

@Keep
data class MaBackup(
    val type: String,
    val period: Int,
    val enabled: Boolean
)

@Keep
data class MacdBackup(
    val fast: Int,
    val slow: Int,
    val signal: Int,
    val enabled: Boolean
)

@Keep
data class ChartIndicators(
    val rsi: List<RsiBackup>,
    val ma: List<MaBackup>,
    val macd: List<MacdBackup>
)

@Keep
data class Settings(
    @SerializedName("balance_primary_value")
    val balanceViewType: BalanceViewType?,
    @SerializedName("app_icon")
    val appIcon: String?,
    @SerializedName("theme_mode")
    val currentTheme: ThemeType?,
    @SerializedName("indicators_shown")
    val chartIndicatorsEnabled: Boolean,
    @SerializedName("indicators")
    val chartIndicators: ChartIndicators,
    @SerializedName("balance_auto_hide")
    val balanceAutoHidden: Boolean?,
    @SerializedName("conversion_token_query_id")
    val conversionTokenQueryId: String?,
    val language: String,
    @SerializedName("launch_screen")
    val launchScreen: LaunchPage?,
    @SerializedName("show_market")
    val marketsTabEnabled: Boolean?,
    @SerializedName("balance_hide_buttons")
    val balanceHideButtons: Boolean?,
    @SerializedName("currency")
    val baseCurrency: String,

    @SerializedName("btc_modes")
    val btcModes: List<BtcMode>,
    @SerializedName("price_change_mode")
    val priceChangeMode: PriceChangeInterval?,
    @SerializedName("evm_sync_sources")
    val evmSyncSources: EvmSyncSources,
    @SerializedName("solana_sync_source")
    val solanaSyncSource: SolanaSyncSource?
)

sealed class RestoreException(message: String) : Exception(message) {
    object EncryptionKeyException : RestoreException("Couldn't get key from passphrase.")
    object InvalidPasswordException :
        RestoreException(Translator.getString(Res.string.ImportBackupFile_Error_InvalidPassword))
}
