package cash.p.terminal.core.providers

import cash.p.terminal.core.IFeeRateProvider
import cash.p.terminal.core.ITorManager
import cash.p.terminal.network.feerate.api.BitcoinFeeRates
import cash.p.terminal.network.feerate.api.FeeRateApi
import io.reactivex.Single
import kotlinx.coroutines.rx2.await
import java.math.BigInteger

class FeeRateProvider(
    private val feeRateApi: FeeRateApi,
    private val torManager: ITorManager,
) {

    suspend fun bitcoinFeeRate(): BitcoinFeeRates = feeRateApi.bitcoinFeeRates(torManager.isTorEnabled)

    fun dogecoinFeeRate(): Single<BigInteger> {
        return Single.just(BigInteger("51000"))
    }

    suspend fun dashFeeRate(): Int = feeRateApi.dashFeeRate(torManager.isTorEnabled)

    fun pirateCashFeeRate(): Single<BigInteger> {
        return Single.just(BigInteger("7000"))
    }

}

class BitcoinFeeRateProvider(private val feeRateProvider: FeeRateProvider) : IFeeRateProvider {
    override val feeRateChangeable = true

    override suspend fun getFeeRates(): FeeRates {
        val bitcoinFeeRate = feeRateProvider.bitcoinFeeRate()
        return FeeRates(bitcoinFeeRate.halfHourFee, bitcoinFeeRate.minimumFee)
    }
}

class LitecoinFeeRateProvider(private val feeRateProvider: FeeRateProvider) : IFeeRateProvider {
    override suspend fun getFeeRates(): FeeRates {
        return FeeRates(2)
    }
}

class DogecoinFeeRateProvider(private val feeRateProvider: FeeRateProvider) : IFeeRateProvider {
    override suspend fun getFeeRates(): FeeRates {
        val feeRate = feeRateProvider.dogecoinFeeRate().await()
        return FeeRates(feeRate.toInt())
    }
}

class BitcoinCashFeeRateProvider(private val feeRateProvider: FeeRateProvider) : IFeeRateProvider {
    override suspend fun getFeeRates(): FeeRates {
        return FeeRates(3)
    }
}

class DashFeeRateProvider(private val feeRateProvider: FeeRateProvider) : IFeeRateProvider {
    override suspend fun getFeeRates(): FeeRates {
        return FeeRates(feeRateProvider.dashFeeRate())
    }
}

class CosantaFeeRateProvider(private val feeRateProvider: FeeRateProvider) : IFeeRateProvider {
    override suspend fun getFeeRates(): FeeRates {
        return FeeRates(feeRateProvider.dashFeeRate())
    }
}

class PirateCashFeeRateProvider(private val feeRateProvider: FeeRateProvider) : IFeeRateProvider {
    override suspend fun getFeeRates(): FeeRates {
        val feeRate = feeRateProvider.pirateCashFeeRate().await()
        return FeeRates(feeRate.toInt())
    }
}

class ECashFeeRateProvider : IFeeRateProvider {
    override suspend fun getFeeRates(): FeeRates {
        return FeeRates(2)
    }
}

data class FeeRates(
    val recommended: Int,
    val minimum: Int = 0,
)
