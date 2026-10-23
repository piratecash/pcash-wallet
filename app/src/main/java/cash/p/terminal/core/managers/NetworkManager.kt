package cash.p.terminal.core.managers

import com.google.gson.JsonObject
import cash.p.terminal.core.INetworkManager
import io.reactivex.Flowable
import io.reactivex.Single
import retrofit2.http.*

class NetworkManager : INetworkManager {

    override suspend fun getMarkdown(host: String, path: String): String {
        return ServiceGuide.service(host).getGuide(path)
    }

    override fun getTransaction(host: String, path: String, isSafeCall: Boolean): Flowable<JsonObject> {
        return ServiceFullTransaction.service(host, isSafeCall).getFullTransaction(path)
    }

    override fun getTransactionWithPost(host: String, path: String, body: Map<String, Any>): Flowable<JsonObject> {
        return ServiceFullTransaction.service(host)
            .getFullTransactionWithPost(path, body.mapValues { it.value.toString() })
    }

    override fun ping(host: String, url: String, isSafeCall: Boolean): Flowable<Any> {
        return ServicePing.service(host, isSafeCall).ping(url)
    }

    override fun getEvmInfo(host: String, path: String): Single<JsonObject> {
        return ServiceEvmContractInfo.service(host).getTokenInfo(path)
    }
}

object ServiceFullTransaction {
    fun service(apiURL: String, isSafeCall: Boolean = true): FullTransactionAPI {
        return APIClient.retrofit(apiURL, 60, isSafeCall)
            .create(FullTransactionAPI::class.java)
    }

    interface FullTransactionAPI {
        @GET
        @Headers("Content-Type: application/json")
        fun getFullTransaction(@Url path: String): Flowable<JsonObject>

        @POST
        @Headers("Content-Type: application/json")
        fun getFullTransactionWithPost(@Url path: String, @Body body: Map<String, String>): Flowable<JsonObject>
    }

}

object ServicePing {
    fun service(apiURL: String, isSafeCall: Boolean = true): FullTransactionAPI {
        return APIClient.retrofit(apiURL, timeout = 8, isSafeCall = isSafeCall).create(FullTransactionAPI::class.java)
    }

    interface FullTransactionAPI {
        @GET
        fun ping(@Url path: String): Flowable<Any>
    }
}

object ServiceEvmContractInfo {
    fun service(apiURL: String): EvmContractInfoAPI {
        return APIClient.retrofit(apiURL, 60)
            .create(EvmContractInfoAPI::class.java)
    }

    interface EvmContractInfoAPI {
        @GET
        @Headers("Content-Type: application/json")
        fun getTokenInfo(@Url path: String): Single<JsonObject>
    }

}


object ServiceGuide {
    fun service(apiURL: String): GuidesAPI {
        return APIClient.retrofit(apiURL, 60, true).create(GuidesAPI::class.java)
    }

    interface GuidesAPI {
        @GET
        suspend fun getGuide(@Url path: String): String
    }
}
