package cash.p.terminal.modules.nft

import androidx.compose.runtime.Composable
import cash.p.terminal.entities.NftItemRecord
import cash.p.terminal.entities.NftTrust
import cash.p.terminal.modules.nft.ui.HIDDEN_NAME
import cash.p.terminal.modules.nft.ui.displayName
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_copy_24px
import cash.p.terminal.resources.ic_eye_off
import cash.p.terminal.resources.ic_fire_24
import cash.p.terminal.resources.ic_globe
import cash.p.terminal.resources.ic_report_24
import cash.p.terminal.resources.nft_burn_action
import cash.p.terminal.resources.nft_copy_address
import cash.p.terminal.resources.nft_hide_and_report
import cash.p.terminal.resources.nft_hide_collection
import cash.p.terminal.resources.nft_hide_nft
import cash.p.terminal.resources.nft_open_in_tonviewer
import cash.p.terminal.resources.nft_unnamed_collection
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

enum class NftAction(
    val title: StringResource,
    val icon: DrawableResource,
    val destructive: Boolean = false,
) {
    HideCollection(Res.string.nft_hide_collection, Res.drawable.ic_eye_off),
    HideNft(Res.string.nft_hide_nft, Res.drawable.ic_eye_off),
    HideAndReport(Res.string.nft_hide_and_report, Res.drawable.ic_report_24),
    OpenExplorer(Res.string.nft_open_in_tonviewer, Res.drawable.ic_globe),
    CopyAddress(Res.string.nft_copy_address, Res.drawable.ic_copy_24px),
    Burn(Res.string.nft_burn_action, Res.drawable.ic_fire_24, destructive = true),
}

/** The menu of the NFT preview; [trust] is the effective one, with the user's own marks applied. */
fun nftActions(
    trust: NftTrust,
    hasCollection: Boolean,
    canBurn: Boolean,
    hasExplorer: Boolean,
): List<NftAction> = buildList {
    add(if (hasCollection) NftAction.HideCollection else NftAction.HideNft)
    if (trust == NftTrust.Graylist || trust == NftTrust.None) add(NftAction.HideAndReport)
    if (hasExplorer) add(NftAction.OpenExplorer)
    if (trust == NftTrust.Whitelist) add(NftAction.CopyAddress)
    if (canBurn) add(NftAction.Burn)
}

/** What the user manages: the collection, or the NFT itself when it has none. */
@Composable
internal fun NftItemRecord.managedTitle(balanceHidden: Boolean): String = when {
    collectionAddress == null -> displayName(balanceHidden)
    balanceHidden -> HIDDEN_NAME
    else -> collectionName.takeUnless { it.isNullOrBlank() } ?: stringResource(Res.string.nft_unnamed_collection)
}
