package cash.p.terminal.core

import cash.p.terminal.R
import cash.p.terminal.entities.FeePriceScale
import cash.p.terminal.wallet.models.HsPointTimePeriod
import io.horizontalsystems.core.entities.BlockchainType

val BlockchainType.feePriceScale: FeePriceScale
    get() = when (this) {
        BlockchainType.Avalanche -> FeePriceScale.Navax
        else -> FeePriceScale.Gwei
    }

val BlockchainType.isUtxoBased: Boolean
    get() = isBtcLike || this == BlockchainType.Zcash


val HsPointTimePeriod.title: Int
    get() = when (this) {
        HsPointTimePeriod.Hour1 -> R.string.Coin_Analytics_Period_1h
        HsPointTimePeriod.Day1 -> R.string.Coin_Analytics_Period_1d
        HsPointTimePeriod.Week1 -> R.string.Coin_Analytics_Period_1w
        HsPointTimePeriod.Month1 -> R.string.Coin_Analytics_Period_1m
        HsPointTimePeriod.Minute30 -> R.string.Coin_Analytics_Period_30m
        HsPointTimePeriod.Hour4 -> R.string.Coin_Analytics_Period_4h
        HsPointTimePeriod.Hour8 -> R.string.Coin_Analytics_Period_8h
    }
