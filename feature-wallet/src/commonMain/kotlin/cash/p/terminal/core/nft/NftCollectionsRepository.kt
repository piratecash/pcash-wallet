package cash.p.terminal.core.nft

import cash.p.terminal.core.managers.OfflineKey
import cash.p.terminal.core.managers.OfflineModeManager
import cash.p.terminal.core.managers.isNetworkPaused
import cash.p.terminal.core.storage.NftItemStorage
import cash.p.terminal.entities.NftItemRecord
import cash.p.terminal.wallet.Wallet
import co.touchlab.kermit.Logger
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

private val logger = Logger.withTag("NftCollectionsRepository")

private val REFRESH_THROTTLE = 30.seconds

data class NftItemsState(val items: List<NftItemRecord>, val isLoaded: Boolean)

class NftCollectionsRepository(
    private val storage: NftItemStorage,
    providers: List<NftProvider>,
    private val offlineModeManager: OfflineModeManager,
    private val dispatcherProvider: DispatcherProvider,
) {
    private val providersByType = providers.associateBy { it.blockchainType }
    private val refreshMutex = Mutex()
    private val fetchedAt = MutableStateFlow<Map<NftOwner, TimeMark>>(emptyMap())

    // Room stays silent when an empty response lands on an empty cache; this publishes that completion.
    private val fetchedOwners = fetchedAt.map { it.keys }.distinctUntilChanged()

    suspend fun owners(wallets: List<Wallet>): Set<NftOwner> =
        wallets.groupBy { it.account.id to it.token.blockchainType }
            .flatMapTo(mutableSetOf()) { (key, group) ->
                val (accountId, blockchainType) = key
                providersByType[blockchainType]?.ownerAddresses(group).orEmpty()
                    .map { NftOwner(accountId, blockchainType, it) }
            }

    suspend fun ownerFor(wallet: Wallet): NftOwner? = owners(listOf(wallet)).firstOrNull()

    fun ownersFlow(wallets: Flow<List<Wallet>>): Flow<Set<NftOwner>> =
        wallets.map { owners(it) }.distinctUntilChanged()

    fun supportsSection(wallet: Wallet): Boolean =
        providersByType[wallet.token.blockchainType]?.supportsSection(wallet) == true

    fun networkTitle(blockchainType: BlockchainType): String? = providersByType[blockchainType]?.networkTitle

    fun explorerUrl(blockchainType: BlockchainType, nftAddress: String): String? =
        providersByType[blockchainType]?.explorerUrl(nftAddress)

    fun changes(owner: NftOwner): Flow<NftChange> =
        providersByType[owner.blockchainType]?.changes(owner.address) ?: emptyFlow()

    /** Emits each time the owners' network goes from paused to available. */
    fun networkResumed(owners: Set<NftOwner>): Flow<Unit> = owners.map { owner ->
        val key = OfflineKey(owner.accountId, owner.blockchainType)
        offlineModeManager.effectiveFlow
            .map { key in it }
            .distinctUntilChanged()
            .drop(1)
            .filter { paused -> !paused }
            .map { }
    }.merge()

    /** All [owners] must belong to one account: it is the only account the query reads. */
    fun itemsFlow(owners: Set<NftOwner>): Flow<NftItemsState> {
        if (owners.isEmpty()) return flowOf(NftItemsState(emptyList(), isLoaded = true))

        val rows = storage.observe(owners.first().accountId, owners.map { it.address })
        return combine(rows, fetchedOwners) { records, fetched ->
            val items = records.filter { it.owner in owners }.distinctBy { it.address }
            NftItemsState(items, isLoaded = items.isNotEmpty() || owners.all { it in fetched })
        }.flowOn(dispatcherProvider.default)
    }

    suspend fun refresh(owners: Set<NftOwner>, force: Boolean) = withContext(dispatcherProvider.io) {
        refreshMutex.withLock {
            owners.forEach { refreshOwner(it, force) }
        }
    }

    private suspend fun refreshOwner(owner: NftOwner, force: Boolean) {
        if (offlineModeManager.isNetworkPaused(owner.accountId, owner.blockchainType)) return
        if (!force && isFetchedRecently(owner)) return
        val provider = providersByType[owner.blockchainType] ?: return

        try {
            val drafts = provider.fetch(owner.address) ?: return
            storage.replace(
                owner.accountId,
                owner.blockchainType,
                owner.address,
                drafts.mapIndexed { position, draft -> draft.toRecord(owner, position) },
            )
            // The account may have been erased while the request was in flight.
            if (offlineModeManager.isForgotten(owner.accountId)) storage.deleteByAccountIds(listOf(owner.accountId))
            fetchedAt.update { it + (owner to TimeSource.Monotonic.markNow()) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A network failure's message can carry the request URL, and with it the address.
            logger.w { "NFT refresh failed for ${owner.blockchainType.uid}: ${e::class.simpleName}" }
        }
    }

    /** Null when the state cannot be read now: the network is paused, there is no source or the request failed. */
    suspend fun liveState(owner: NftOwner, nftAddress: String): NftLiveState? =
        networkRead(owner, "live state") { liveState(owner.address, nftAddress) }

    /** Sorted by expiry; null when no owner could be read now. */
    suspend fun expiringDomains(owners: Set<NftOwner>, periodDays: Int): List<NftDomainExpiry>? {
        val reads = owners.mapNotNull { owner ->
            networkRead(owner, "domain expiry") { expiringDomains(owner.address, periodDays) }
                ?.map { it.toExpiry(owner) }
        }
        if (reads.isEmpty() && owners.isNotEmpty()) return null
        return reads.flatten().sortedBy { it.expiresAt }
    }

    /** Emits when the owners' domains may have changed: a kit started, a new event arrived or the network resumed. */
    fun domainExpiryTriggers(owners: Set<NftOwner>): Flow<Unit> = merge(
        owners.map(::changes).merge().map { },
        networkResumed(owners),
    )

    /** Reads on start, on [extraTriggers] and on [domainExpiryTriggers]; a null read keeps what the caller showed before. */
    fun expiringDomainsFlow(
        owners: Set<NftOwner>,
        periodDays: Int,
        extraTriggers: Flow<Unit>,
    ): Flow<List<NftDomainExpiry>> = merge(flowOf(Unit), extraTriggers, domainExpiryTriggers(owners))
        .map { expiringDomains(owners, periodDays) }
        .filterNotNull()

    private suspend fun <T> networkRead(owner: NftOwner, what: String, read: suspend NftProvider.() -> T?): T? =
        withContext(dispatcherProvider.io) {
            if (offlineModeManager.isNetworkPaused(owner.accountId, owner.blockchainType)) return@withContext null
            val provider = providersByType[owner.blockchainType] ?: return@withContext null
            try {
                provider.read()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A network failure's message can carry the request URL, and with it the address.
                logger.w { "NFT $what failed for ${owner.blockchainType.uid}: ${e::class.simpleName}" }
                null
            }
        }

    private fun isFetchedRecently(owner: NftOwner): Boolean =
        fetchedAt.value[owner]?.let { it.elapsedNow() < REFRESH_THROTTLE } == true
}

internal val NftItemRecord.owner: NftOwner
    get() = NftOwner(accountId, blockchainType, ownerAddress)

private fun NftDomainDraft.toExpiry(owner: NftOwner) =
    NftDomainExpiry(owner, nftAddress, name, expiresAt, isOnSale, imageUrl)

private fun NftItemRecordDraft.toRecord(owner: NftOwner, position: Int) = NftItemRecord(
    accountId = owner.accountId,
    blockchainType = owner.blockchainType,
    ownerAddress = owner.address,
    address = address,
    position = position,
    name = name,
    description = description,
    realOwnerAddress = realOwnerAddress,
    collectionAddress = collectionAddress,
    collectionName = collectionName,
    collectionDescription = collectionDescription,
    trust = trust,
    verified = verified,
    imageUrl = imageUrl,
    largeImageUrl = largeImageUrl,
    inSale = inSale,
    lottieUrl = lottieUrl,
)
