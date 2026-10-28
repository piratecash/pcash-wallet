package cash.p.terminal.core

import cash.p.terminal.entities.FeePriceScale
import cash.p.terminal.wallet.models.HsPointTimePeriod
import io.horizontalsystems.core.entities.BlockchainType
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Coin_Analytics_Period_1d
import cash.p.terminal.resources.Coin_Analytics_Period_1h
import cash.p.terminal.resources.Coin_Analytics_Period_1m
import cash.p.terminal.resources.Coin_Analytics_Period_1w
import cash.p.terminal.resources.Coin_Analytics_Period_30m
import cash.p.terminal.resources.Coin_Analytics_Period_4h
import cash.p.terminal.resources.Coin_Analytics_Period_8h
import org.jetbrains.compose.resources.StringResource

val BlockchainType.feePriceScale: FeePriceScale
    get() = when (this) {
        BlockchainType.Avalanche -> FeePriceScale.Navax
        else -> FeePriceScale.Gwei
    }

val BlockchainType.isUtxoBased: Boolean
    get() = isBtcLike || this == BlockchainType.Zcash


val HsPointTimePeriod.title: StringResource
    get() = when (this) {
        HsPointTimePeriod.Hour1 -> Res.string.Coin_Analytics_Period_1h
        HsPointTimePeriod.Day1 -> Res.string.Coin_Analytics_Period_1d
        HsPointTimePeriod.Week1 -> Res.string.Coin_Analytics_Period_1w
        HsPointTimePeriod.Month1 -> Res.string.Coin_Analytics_Period_1m
        HsPointTimePeriod.Minute30 -> Res.string.Coin_Analytics_Period_30m
        HsPointTimePeriod.Hour4 -> Res.string.Coin_Analytics_Period_4h
        HsPointTimePeriod.Hour8 -> Res.string.Coin_Analytics_Period_8h
    }
