package cash.p.terminal.network.feerate.api

interface FeeRateApi {
    suspend fun bitcoinFeeRates(torEnabled: Boolean): BitcoinFeeRates

    /** Returns a static fallback rate on any request failure or after 10 s without a response. */
    suspend fun dashFeeRate(torEnabled: Boolean): Int
}

data class BitcoinFeeRates(val halfHourFee: Int, val minimumFee: Int)
