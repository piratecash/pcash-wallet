package cash.p.terminal.core.managers

import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TonWalletVersion
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.logger.AppLogger
import io.horizontalsystems.tonkit.models.Event
import io.horizontalsystems.tonkit.models.Jetton
import io.horizontalsystems.tonkit.models.SyncState
import io.horizontalsystems.tonkit.models.Tag
import io.horizontalsystems.tonkit.models.TagQuery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

class TonAccountManager(
    private val accountManager: IAccountManager,
    private val walletManager: IWalletManager,
    private val tonKitManager: TonKitManager,
    private val tokenAutoEnableManager: TokenAutoEnableManager,
    private val userDeletedWalletManager: UserDeletedWalletManager,
    private val marketKit: MarketKitWrapper,
) {
    private val blockchainType: BlockchainType = BlockchainType.Ton
    private val logger = AppLogger("ton-account-manager")
    private val singleDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val singleDispatcherCoroutineScope = CoroutineScope(singleDispatcher)
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    // Confined to singleDispatcher.
    private val subscriptions = mutableMapOf<TonWalletVersion, Subscription>()

    fun start() {
        singleDispatcherCoroutineScope.launch {
            tonKitManager.startedKitsFlow.collect { wrappers ->
                handleStarted(wrappers)
            }
        }
    }

    private fun handleStarted(wrappers: Map<TonWalletVersion, TonKitWrapper>) {
        try {
            cancelReplacedSubscriptions(wrappers)
            wrappers.filterKeys { it !in subscriptions }.forEach { (version, wrapper) ->
                subscribeToTransactions(wrapper)?.let { subscriptions[version] = it }
            }
        } catch (exception: Exception) {
            logger.warning("error", exception)
        }
    }

    private fun cancelReplacedSubscriptions(wrappers: Map<TonWalletVersion, TonKitWrapper>) {
        val replaced = subscriptions.filter { (version, subscription) -> wrappers[version] !== subscription.wrapper }
        replaced.values.forEach { it.job.cancel() }
        subscriptions.keys.removeAll(replaced.keys)
    }

    private fun subscribeToTransactions(tonKitWrapper: TonKitWrapper): Subscription? {
        val account = accountManager.activeAccount ?: return null

        val job = coroutineScope.launch {
            if (tonKitWrapper.version.discoversWholeHistory) {
                launch { handleStoredJettonEvents(account, tonKitWrapper) }
            }
            tonKitWrapper.tonKit.eventFlow(TagQuery(null, null, null, null))
                .collect { (events, initial) ->
                    handle(events, account, tonKitWrapper, initial)
                }
        }
        return Subscription(tonKitWrapper, job)
    }

    // The event flow does not replay, so history the kit synced before we subscribed is read from its DB.
    private suspend fun handleStoredJettonEvents(account: Account, tonKitWrapper: TonKitWrapper) {
        val tonKit = tonKitWrapper.tonKit
        try {
            tonKit.eventSyncStateFlow.first { it is SyncState.Synced }
            var beforeLt: Long? = null
            do {
                val events = tonKit.events(jettonEventsQuery, beforeLt, STORED_EVENTS_PAGE_SIZE)
                handle(events, account, tonKitWrapper, initial = true)
                beforeLt = events.lastOrNull()?.lt
            } while (events.size == STORED_EVENTS_PAGE_SIZE)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            logger.warning("stored jetton events scan failed", exception)
        }
    }

    private suspend fun handle(
        events: List<Event>,
        account: Account,
        tonKitWrapper: TonKitWrapper,
        initial: Boolean,
    ) {
        val shouldAutoEnableTokens = tokenAutoEnableManager.isAutoEnabled(account, blockchainType)

        val skipsInitialBatch = !tonKitWrapper.version.discoversWholeHistory &&
            isRestoredWithoutAutoEnable(account, shouldAutoEnableTokens)
        if (initial && skipsInitialBatch) return

        val address = tonKitWrapper.tonKit.receiveAddress

        val jettons = mutableSetOf<Jetton>()

        events.forEach { event ->
            event.actions.forEach { action ->
                action.jettonTransfer?.let {
                    if (it.recipient?.address == address) {
                        jettons.add(it.jetton)
                    }
                }
                action.jettonMint?.let {
                    if (it.recipient.address == address) {
                        jettons.add(it.jetton)
                    }
                }
                action.jettonSwap?.let {
                    it.jettonMasterIn?.let { jetton ->
                        jettons.add(jetton)
                    }
                }
            }
        }

        handle(jettons, account, tonKitWrapper.version)
    }

    internal suspend fun handle(jettons: Set<Jetton>, account: Account, version: TonWalletVersion) {
        if (jettons.isEmpty()) return

        val existingTokenTypeIds = walletManager.activeWallets.map { it.token.type.id }
        val newJettonTypes = jettons
            .map { it.tokenType(version) }
            .filter { it.id !in existingTokenTypeIds }

        if (newJettonTypes.isEmpty()) return

        val tokenInfos = try {
            val queries = newJettonTypes.map { TokenQuery(blockchainType, it) }
            val knownTokens = marketKit.tokensChunked(queries)
            filterKnownAutoEnableTokens(newJettonTypes, knownTokens)
        } catch (ex: Exception) {
            logger.warning("marketKit lookup failed", ex)
            return
        }

        if (tokenInfos.isEmpty()) return

        val enabledWallets = tokenInfos.toEnabledWallets(
            accountId = account.id,
            blockchainType = blockchainType,
            userDeletedWalletManager = userDeletedWalletManager,
        )

        if (enabledWallets.isEmpty()) return

        walletManager.saveEnabledWallets(enabledWallets)
    }

    private fun isRestoredWithoutAutoEnable(account: Account, autoEnabled: Boolean) =
        account.origin == AccountOrigin.Restored && !account.isWatchAccount && !autoEnabled

    private class Subscription(val wrapper: TonKitWrapper, val job: Job)

    private companion object {
        const val STORED_EVENTS_PAGE_SIZE = 100
        val jettonEventsQuery = TagQuery(null, Tag.Platform.Jetton, null, null)
    }
}

/** Enabling W5 is an explicit user action, so the jettons its whole history holds are always discovered. */
private val TonWalletVersion.discoversWholeHistory: Boolean
    get() = when (this) {
        TonWalletVersion.V4R2 -> false
        TonWalletVersion.W5 -> true
    }
