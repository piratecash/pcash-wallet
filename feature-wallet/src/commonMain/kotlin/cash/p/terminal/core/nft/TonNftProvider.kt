package cash.p.terminal.core.nft

import cash.p.terminal.core.managers.TonKitManager
import cash.p.terminal.core.managers.TonKitWrapper
import cash.p.terminal.core.managers.normalizedTokenLabel
import cash.p.terminal.entities.NftTrust
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.TonAsset
import cash.p.terminal.wallet.entities.TonWalletVersion
import cash.p.terminal.wallet.entities.tonAsset
import co.touchlab.kermit.Logger
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.tonkit.Address
import io.horizontalsystems.tonkit.core.TonKit
import io.horizontalsystems.tonkit.models.NftItem
import io.horizontalsystems.tonkit.models.NftPreview
import io.horizontalsystems.tonkit.models.TagQuery
import io.horizontalsystems.tonkit.models.NftTrust as KitNftTrust
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import kotlin.math.abs

private val logger = Logger.withTag("TonNftProvider")

private const val HIDDEN_RENDER_TYPE = "hidden"
private const val NAME_MAX_LENGTH = 128
private const val DESCRIPTION_MAX_LENGTH = 2000
private const val CARD_IMAGE_SIDE = 500
private const val LARGE_IMAGE_SIDE = 1500
private const val SECURE_URL_PREFIX = "https://"
private val ALL_EVENTS = TagQuery(null, null, null, null)

class TonNftProvider(
    private val tonKitManager: TonKitManager,
    private val dispatcherProvider: DispatcherProvider,
) : NftProvider {

    override val blockchainType = BlockchainType.Ton
    override val networkTitle = "TON"

    override fun explorerUrl(nftAddress: String) = "https://tonviewer.com/nft/$nftAddress"

    override fun supportsSection(wallet: Wallet) = wallet.token.tonAsset is TonAsset.Native

    override suspend fun ownerAddresses(wallets: List<Wallet>): Set<String> = withContext(dispatcherProvider.io) {
        wallets
            .mapNotNull { wallet -> wallet.token.tonAsset?.let { wallet.account to it.version } }
            .distinct()
            .mapNotNullTo(mutableSetOf()) { (account, version) -> receiveAddressOrNull(account, version) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun changes(address: String): Flow<NftChange> = tonKitManager.startedKitsFlow
        .map { kits -> kits.kitOwnedBy(address) }
        .distinctUntilChanged()
        .flatMapLatest { kit -> kit?.changes() ?: emptyFlow() }

    override suspend fun fetch(address: String): List<NftItemRecordDraft>? =
        tonKitManager.startedKitsFlow.value.kitOwnedBy(address)
            ?.nftItems()
            ?.mapNotNull { it.toDraftOrNull() }

    override suspend fun liveState(ownerAddress: String, nftAddress: String): NftLiveState? =
        tonKitManager.startedKitsFlow.value.kitOwnedBy(ownerAddress)
            ?.nftItem(Address.parse(nftAddress))
            ?.let { NftLiveState(it.ownerAddress, it.isOnSale, it.dns.sanitized(NAME_MAX_LENGTH)) }

    // The failure's message can carry the account id, so only its type is logged.
    private fun receiveAddressOrNull(account: Account, version: TonWalletVersion): String? = try {
        tonKitManager.getReceiveAddress(account, version)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logger.w { "Skipped ${version.title} wallet: ${e::class.simpleName}" }
        null
    }
}

// Same form as TonKitManager.getReceiveAddress, which is what owner addresses are built from.
private fun Map<TonWalletVersion, TonKitWrapper>.kitOwnedBy(address: String): TonKit? =
    values.firstOrNull { it.tonKit.receiveAddress.toUserFriendly(false) == address }?.tonKit

private fun TonKit.changes(): Flow<NftChange> = eventFlow(ALL_EVENTS)
    .map<_, NftChange> { NftChange.NewEvent }
    .onStart { emit(NftChange.KitAppeared) }

private fun NftItem.toDraftOrNull(): NftItemRecordDraft? {
    if (renderType == HIDDEN_RENDER_TYPE) return null
    val appTrust = trust.toAppTrust() ?: return null
    return NftItemRecordDraft(
        address = address,
        name = name.sanitized(NAME_MAX_LENGTH),
        description = description.sanitized(DESCRIPTION_MAX_LENGTH),
        realOwnerAddress = ownerAddress,
        collectionAddress = collection?.address,
        collectionName = collection?.name.sanitized(NAME_MAX_LENGTH),
        collectionDescription = collection?.description.sanitized(DESCRIPTION_MAX_LENGTH),
        trust = appTrust,
        verified = verified,
        imageUrl = previews.nearestSecureUrl(CARD_IMAGE_SIDE),
        largeImageUrl = previews.nearestSecureUrl(LARGE_IMAGE_SIDE),
    )
}

private fun KitNftTrust.toAppTrust(): NftTrust? = when (this) {
    KitNftTrust.WHITELIST -> NftTrust.Whitelist
    KitNftTrust.GRAYLIST -> NftTrust.Graylist
    KitNftTrust.NONE -> NftTrust.None
    KitNftTrust.BLACKLIST -> null
}

private fun String?.sanitized(maxLength: Int): String? =
    this?.normalizedTokenLabel()?.take(maxLength)?.trim()?.ifBlank { null }

private fun List<NftPreview>.nearestSecureUrl(side: Int): String? =
    filter { it.url.startsWith(SECURE_URL_PREFIX) }
        .minByOrNull { abs(it.side - side) }
        ?.url

private val NftPreview.side: Int
    get() = resolution.substringBefore('x').toIntOrNull() ?: 0
