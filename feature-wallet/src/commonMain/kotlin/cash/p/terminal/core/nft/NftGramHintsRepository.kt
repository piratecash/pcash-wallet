package cash.p.terminal.core.nft

import cash.p.terminal.core.AccountTokenSupport
import cash.p.terminal.core.BalanceSettingsStorage
import cash.p.terminal.core.managers.OfflineModeManager
import cash.p.terminal.core.managers.WalletActivator
import cash.p.terminal.core.managers.isNetworkPaused
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.IDeletedWalletRestorer
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TonAsset
import cash.p.terminal.wallet.entities.TonWalletVersion
import cash.p.terminal.wallet.entities.tokenQuery
import cash.p.terminal.wallet.entities.tonAsset
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** GRAM versions an account can add, and the NFTs found on the ones it has not added. */
class NftGramHintsRepository(
    private val marketKit: MarketKitWrapper,
    private val accountTokenSupport: AccountTokenSupport,
    private val tonNftProvider: TonNftProvider,
    private val offlineModeManager: OfflineModeManager,
    private val settings: BalanceSettingsStorage,
    private val walletActivator: WalletActivator,
    private val deletedWalletRestorer: IDeletedWalletRestorer,
) {
    // Successful counts only, kept until the process restarts; a failed read is retried next time.
    private val counts = MutableStateFlow<Map<CountKey, Int>>(emptyMap())
    private val countMutex = Mutex()

    fun eligibleVersions(account: Account): List<TonWalletVersion> = TonWalletVersion.entries.filter { version ->
        marketKit.token(version.nativeTokenQuery)?.let { accountTokenSupport.supports(it, account.type) } == true
    }

    fun addedVersions(wallets: List<Wallet>): Set<TonWalletVersion> =
        wallets.mapNotNullTo(mutableSetOf()) { (it.token.tonAsset as? TonAsset.Native)?.version }

    fun isDismissed(account: Account, version: TonWalletVersion) =
        dismissKey(account, version) in settings.dismissedNftVersionHints

    fun dismiss(account: Account, version: TonWalletVersion) {
        settings.dismissedNftVersionHints += dismissKey(account, version)
    }

    fun cachedNftCounts(account: Account, versions: List<TonWalletVersion>): Map<TonWalletVersion, Int> =
        versions.mapNotNull { version -> counts.value[CountKey(account.id, version)]?.let { version to it } }.toMap()

    /** Reads the versions not counted yet; a watched address or a paused network is never read. */
    suspend fun nftCounts(account: Account, versions: List<TonWalletVersion>): Map<TonWalletVersion, Int> {
        if (!account.isWatchAccount) {
            countMutex.withLock {
                versions.filter { CountKey(account.id, it) !in counts.value }.forEach { version ->
                    // Checked per read: the user may pause TON while the previous version is being read.
                    if (offlineModeManager.isNetworkPaused(account, BlockchainType.Ton)) return@withLock
                    tonNftProvider.countNfts(account, version)?.let { count ->
                        counts.update { it + (CountKey(account.id, version) to count) }
                    }
                }
            }
        }
        return cachedNftCounts(account, versions)
    }

    /** False when the version needs the coin manager, e.g. a hardware card that has no TON key yet. */
    suspend fun add(account: Account, version: TonWalletVersion): Boolean {
        if (!tonNftProvider.hasAddress(account, version)) return false
        val query = version.nativeTokenQuery
        deletedWalletRestorer.unmarkAsDeleted(account.id, listOf(query.id))
        walletActivator.activateWalletsSuspended(account, listOf(query))
        return true
    }

    private fun dismissKey(account: Account, version: TonWalletVersion) = "${account.id}:${version.name}"

    private data class CountKey(val accountId: String, val version: TonWalletVersion)
}

private val TonWalletVersion.nativeTokenQuery: TokenQuery
    get() = TonAsset.Native(this).tokenQuery
