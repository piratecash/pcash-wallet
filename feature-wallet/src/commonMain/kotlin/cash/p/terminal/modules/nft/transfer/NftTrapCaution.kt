package cash.p.terminal.modules.nft.transfer

import cash.p.terminal.core.HSCaution
import cash.p.terminal.entities.CoinValue
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_attention_24
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.wallet.Token
import org.jetbrains.compose.resources.StringResource

/** The emulated transfer leaves the NFT with its owner and the attached TON with the item's contract. */
internal fun nftTrapCaution(title: StringResource, body: StringResource, token: Token) = HSCaution(
    s = TranslatableString.ResString(title),
    description = TranslatableString.ResString(body, nftTrapAttachedText(token)),
    icon = Res.drawable.ic_attention_24,
)

internal fun nftTrapAttachedText(token: Token): String = CoinValue(token, NFT_TRANSFER_ATTACHED).getFormattedFull()
