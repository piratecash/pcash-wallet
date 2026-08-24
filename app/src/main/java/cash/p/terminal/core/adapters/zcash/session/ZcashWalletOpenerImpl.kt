package cash.p.terminal.core.adapters.zcash.session

import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.UnsupportedAccountException
import cash.p.terminal.core.adapters.zcash.ZcashKey
import cash.p.terminal.core.adapters.zcash.zcashKey
import cash.p.terminal.core.managers.RestoreSettingsManager
import cash.p.terminal.core.managers.ZcashBirthdayProvider
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.Wallet
import cash.p.zcash.ServerConfig
import cash.p.zcash.Transport
import cash.p.zcash.ZcashNetwork
import cash.p.zcash.ZcashSdk
import cash.p.zcash.ZcashWallet
import io.horizontalsystems.core.entities.BlockchainType

class ZcashWalletOpenerImpl(
    private val databaseFiles: ZcashDatabaseFiles,
    private val localStorage: ILocalStorage,
    private val restoreSettingsManager: RestoreSettingsManager,
    private val birthdayProvider: ZcashBirthdayProvider,
    private val dbKeyProvider: ZcashDbKeyProvider,
) : ZcashWalletOpener {

    override suspend fun open(wallet: Wallet): OpenedZcashWallet {
        databaseFiles.dataDir.mkdirs()
        ZcashSdk.initialize(databaseFiles.dataDir.absolutePath, databaseFiles.legacyDir.absolutePath)

        val accountId = wallet.account.id
        val dbKey = dbKeyProvider.keyFor(accountId)
        if (dbKey.newlyGenerated) databaseFiles.delete(accountId)

        val dbFile = databaseFiles.databaseFile(accountId)
        val zcashWallet = ZcashWallet.open(dbFile.path, ZcashNetwork.MAIN, serverConfig(), dbKey.bytes)
        val dbAccountId = zcashWallet.accounts().firstOrNull()?.id ?: restore(zcashWallet, wallet)
        discoverTransparentAddresses(zcashWallet, accountId, dbAccountId)
        return OpenedZcashWallet(zcashWallet, dbAccountId)
    }

    private suspend fun restore(zcashWallet: ZcashWallet, wallet: Wallet): Int {
        val account = wallet.account
        val key = wallet.zcashKey() ?: throw UnsupportedAccountException()

        return zcashWallet.restoreAccount(
            name = account.name,
            key = when (key) {
                is ZcashKey.Phrase -> key.words.joinToString(" ")
                is ZcashKey.ViewingKey -> key.key
            },
            birthHeight = birthHeight(account),
            passphrase = (key as? ZcashKey.Phrase)?.passphrase?.takeIf { it.isNotBlank() },
        )
    }

    /** Zero lets the SDK clamp each pool to its own activation height. */
    private fun birthHeight(account: Account): Int {
        val stored = restoreSettingsManager.settings(account, BlockchainType.Zcash).birthdayHeight
        return when {
            stored != null && stored > 0 -> stored.toInt()
            account.origin == AccountOrigin.Created ->
                // Birthday persistence predates the P.CASH fork, so a missing value cannot
                // identify a migrated P.CASH wallet. Avoid a full scan for incomplete setup data.
                birthdayProvider.getLatestCheckpointBlockHeight().toInt()

            else -> 0
        }
    }

    /**
     * One-time addresses are not derived by a restore, and looking for them once the pool has
     * reached the tip finds nothing — so it has to happen before the first sync, and it has to
     * survive being offline, hence the flag is only raised on success.
     */
    private suspend fun discoverTransparentAddresses(
        zcashWallet: ZcashWallet,
        accountId: String,
        dbAccountId: Int,
    ) {
        if (accountId in localStorage.zcashDiscoveredAccountIds) return
        tryOrNull { zcashWallet.discoverTransparentAddresses(dbAccountId) } ?: return
        localStorage.zcashDiscoveredAccountIds += accountId
    }

    private fun serverConfig() = ServerConfig(
        url = SERVER_URL,
        transport = if (localStorage.torEnabled) Transport.TOR else Transport.DIRECT,
    )

    private companion object {
        const val SERVER_URL = "https://zec.rocks:443"
    }
}
