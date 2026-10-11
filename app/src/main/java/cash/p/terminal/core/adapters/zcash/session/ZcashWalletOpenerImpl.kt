package cash.p.terminal.core.adapters.zcash.session

import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.UnsupportedAccountException
import cash.p.terminal.core.adapters.zcash.ZcashKey
import cash.p.terminal.core.adapters.zcash.isZcashDatabaseFailure
import cash.p.terminal.core.adapters.zcash.zcashAppLogger
import cash.p.terminal.core.adapters.zcash.zcashKey
import cash.p.terminal.core.managers.RestoreSettingsManager
import cash.p.terminal.core.managers.ZcashBirthdayProvider
import cash.p.terminal.core.managers.ZcashServerManager
import cash.p.terminal.core.managers.sanitizeNetworkUrl
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.Wallet
import cash.p.zcash.AccountInfo
import cash.p.zcash.ServerConfig
import cash.p.zcash.ZcashException
import cash.p.zcash.ZcashNetwork
import cash.p.zcash.ZcashSdk
import cash.p.zcash.ZcashWallet
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.logger.AppLogger
import kotlin.time.TimeSource

class ZcashWalletOpenerImpl(
    private val databaseFiles: ZcashDatabaseFiles,
    private val localStorage: ILocalStorage,
    private val restoreSettingsManager: RestoreSettingsManager,
    private val birthdayProvider: ZcashBirthdayProvider,
    private val dbKeyProvider: ZcashDbKeyProvider,
    private val serverManager: ZcashServerManager,
) : ZcashWalletOpener {

    private class Birth(val source: BirthSource, val height: Int)

    private enum class BirthSource { SETTING, CHECKPOINT, NONE }

    override suspend fun open(wallet: Wallet): OpenedZcashWallet {
        val started = TimeSource.Monotonic.markNow()
        val accountId = wallet.account.id
        val logger = zcashAppLogger(accountId)
        val dirState = dirState()
        ZcashSdk.initialize(databaseFiles.dataDir.absolutePath, databaseFiles.legacyDir.absolutePath)

        val dbKey = dbKeyProvider.keyFor(accountId)
        val dbExists = databaseFiles.databaseFile(accountId).exists()
        if (dbKey.newlyGenerated && dbExists) {
            // Deletes the coverage record with the file it lives in: the freshly-opened wallet
            // reads back an absent record, so its first discovery walk is a deep one. The pristine
            // mark goes with the rows that made the account pristine.
            databaseFiles.delete(accountId)
        }
        val deepSweepRequired = !databaseFiles.isPristine(accountId)

        val server = serverConfig()
        logger.info(
            "open: db=${if (dbExists) "exists" else "absent"} " +
                "key=${if (dbKey.newlyGenerated) "new" else "stored"} " +
                "deepSweep=$deepSweepRequired " +
                "legacyDir=${if (databaseFiles.legacyDir.exists()) "present" else "absent"} " +
                "server=${sanitizeNetworkUrl(server.url)} transport=${server.transport} " +
                "accountType=${wallet.account.type::class.simpleName} dir=$dirState"
        )
        val (zcashWallet, existing, dbAccountId) = openAndRestore(wallet, server, dbKey, logger)
        logger.info(
            "open: done dbAccountId=$dbAccountId accounts=${existing.size} " +
                "elapsed=${started.elapsedNow().inWholeMilliseconds}ms"
        )
        return OpenedZcashWallet(zcashWallet, dbAccountId, deepSweepRequired, server.url)
    }

    private fun dirState(): String = when {
        databaseFiles.dataDir.mkdirs() -> "created"
        databaseFiles.dataDir.exists() -> "exists"
        else -> "failed"
    }

    private suspend fun openAndRestore(
        wallet: Wallet,
        server: ServerConfig,
        dbKey: ZcashDbKey,
        logger: AppLogger,
    ): Triple<ZcashWallet, List<AccountInfo>, Int> {
        val accountId = wallet.account.id
        val dbPath = databaseFiles.databaseFile(accountId).path
        try {
            val zcashWallet = ZcashWallet.open(dbPath, ZcashNetwork.MAIN, server, dbKey.bytes)
            val existing = zcashWallet.accounts()
            val dbAccountId = existing.firstOrNull()?.id ?: restore(zcashWallet, wallet, logger)
            return Triple(zcashWallet, existing, dbAccountId)
        } catch (e: ZcashException) {
            if (e.isZcashDatabaseFailure()) logger.warning(databaseFiles.diagnostics(accountId))
            throw e
        }
    }

    private suspend fun restore(zcashWallet: ZcashWallet, wallet: Wallet, logger: AppLogger): Int {
        val account = wallet.account
        val key = wallet.zcashKey() ?: throw UnsupportedAccountException()
        val birth = birth(account)
        logger.info("restore: birthSource=${birth.source.name.lowercase()} birth=${birth.height}")

        return zcashWallet.restoreAccount(
            name = account.name,
            key = when (key) {
                is ZcashKey.Phrase -> key.words.joinToString(" ")
                is ZcashKey.Standalone -> key.key
            },
            birthHeight = birth.height,
            passphrase = (key as? ZcashKey.Phrase)?.passphrase,
        )
    }

    /** Zero lets the SDK clamp each pool to its own activation height. */
    private fun birth(account: Account): Birth {
        val stored = restoreSettingsManager.settings(account, BlockchainType.Zcash).birthdayHeight
        return when {
            stored != null && stored > 0 -> Birth(BirthSource.SETTING, stored.toInt())
            account.origin == AccountOrigin.Created ->
                // Birthday persistence predates the P.CASH fork, so a missing value cannot
                // identify a migrated P.CASH wallet. Avoid a full scan for incomplete setup data.
                Birth(BirthSource.CHECKPOINT, birthdayProvider.getLatestCheckpointBlockHeight().toInt())

            else -> Birth(BirthSource.NONE, 0)
        }
    }

    private fun serverConfig(): ServerConfig {
        val url = serverManager.current.url
        return ServerConfig(url = url, transport = serverManager.transportFor(url, localStorage.torEnabled))
    }
}
