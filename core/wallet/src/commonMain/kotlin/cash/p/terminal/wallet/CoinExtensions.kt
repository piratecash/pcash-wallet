package cash.p.terminal.wallet

import cash.p.terminal.resources.Res
import cash.p.terminal.resources.coin_placeholder
import cash.p.terminal.wallet.entities.Coin
import org.jetbrains.compose.resources.DrawableResource

fun coinImageUrl(coinUid: String): String =
    "https://p.cash/storage/coins/$coinUid/image.png"

val Coin.imageUrl: String
    get() = coinImageUrl(uid)

val Coin.alternativeImageUrl: String?
    get() = image

val Coin.imagePlaceholder: DrawableResource
    get() = Res.drawable.coin_placeholder
