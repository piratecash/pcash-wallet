package cash.p.terminal.wallet

import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.FullCoin
import cash.p.terminal.wallet.entities.TokenType

fun coinImageUrl(coinUid: String): String =
    "https://p.cash/storage/coins/$coinUid/image.png"

val Coin.imageUrl: String
    get() = coinImageUrl(uid)

val Coin.alternativeImageUrl: String?
    get() = image

val Coin.imagePlaceholder: Int
    get() = R.drawable.coin_placeholder

/** A THORChain secured asset: a copy of another coin, which its coinGeckoId points at. */
val FullCoin.isSynthetic: Boolean
    get() = coin.coinGeckoId.let { it != null && it != coin.uid } &&
        tokens.isNotEmpty() &&
        tokens.all { it.type is TokenType.ThorchainAsset }
