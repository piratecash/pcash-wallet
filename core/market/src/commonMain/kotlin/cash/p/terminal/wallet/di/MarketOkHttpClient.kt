package cash.p.terminal.wallet.di

import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

internal const val RETROFIT_OK_HTTP_CLIENT = "retrofitOkHttpClient"

/** Shared by both platform modules so the market timeouts and logging stay in one place. */
internal fun marketOkHttpClient(cache: Cache?): OkHttpClient {
    val loggingInterceptor = HttpLoggingInterceptor().apply {
        setLevel(HttpLoggingInterceptor.Level.BASIC)
    }

    return OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .cache(cache)
        .build()
}
