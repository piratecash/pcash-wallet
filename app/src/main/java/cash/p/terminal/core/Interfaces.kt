package cash.p.terminal.core

import com.google.gson.JsonObject
import cash.p.terminal.modules.market.MarketModule
import cash.p.terminal.modules.settings.security.tor.TorStatus
import io.reactivex.Flowable
import io.reactivex.Single
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface IMarketStorage {
    var currentMarketTab: MarketModule.Tab?
}

interface INetworkManager {
    suspend fun getMarkdown(host: String, path: String): String
    fun getTransaction(host: String, path: String, isSafeCall: Boolean): Flowable<JsonObject>
    fun getTransactionWithPost(
        host: String,
        path: String,
        body: Map<String, Any>
    ): Flowable<JsonObject>

    fun ping(host: String, url: String, isSafeCall: Boolean): Flowable<Any>
    fun getEvmInfo(host: String, path: String): Single<JsonObject>
}

interface ITorManager {
    fun start()
    fun stop(): Single<Boolean>
    fun setTorAsEnabled()
    fun setTorAsDisabled()
    val isTorEnabled: Boolean
    val torStatusFlow: StateFlow<TorStatus>
}

interface IRateAppManager {
    val showRateAppFlow: Flow<Boolean>

    fun onBalancePageActive()
    fun onBalancePageInactive()
    fun onAppLaunch()
}
