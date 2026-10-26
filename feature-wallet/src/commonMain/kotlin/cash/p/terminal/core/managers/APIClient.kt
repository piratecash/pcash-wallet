package cash.p.terminal.core.managers

import com.google.gson.GsonBuilder
import com.google.gson.Strictness
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object APIClient {

    private val logger = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private fun buildClient(headers: Map<String, String>): OkHttpClient {
        val headersInterceptor = Interceptor { chain ->
            val requestBuilder = chain.request().newBuilder()
            headers.forEach { (name, value) ->
                requestBuilder.header(name, value)
            }
            chain.proceed(requestBuilder.build())
        }

        return OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(logger)
            .addInterceptor(headersInterceptor)
            .build()
    }

    fun build(baseUrl: String, headers: Map<String, String> = mapOf()): Retrofit {
        val client = buildClient(headers)

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .addConverterFactory(
                GsonConverterFactory.create(GsonBuilder().setLenient().create())
            )
            .build()
    }

    //share OkHttpClient
    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(logger)
        .build()

    val gson by lazy {
        val gsonBuilder = GsonBuilder().setStrictness(Strictness.LENIENT)
        gsonBuilder.create()
    }

    fun retrofit(apiURL: String, timeout: Long = 60, isSafeCall: Boolean = true): Retrofit {

        val httpClient = okHttpClient.newBuilder()
            .connectTimeout(timeout, TimeUnit.SECONDS)
            .readTimeout(timeout, TimeUnit.SECONDS)

        //TODO Replace this implementation with Manifest file settings when support for SDK 26 removed
        if (!isSafeCall) // if host name cannot be verified, has no or self signed certificate, do unsafe request
            setUnsafeSocketFactory(httpClient)

        return Retrofit.Builder()
            .baseUrl(apiURL)
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .client(httpClient.build())
            .build()
    }

    @Suppress("TrustAllX509TrustManager", "BadHostnameVerifier")
    private fun setUnsafeSocketFactory(builder: OkHttpClient.Builder) {
        try {
            val trustAllCerts = arrayOf<TrustManager>(
                object : X509TrustManager {
                    @Throws(CertificateException::class)
                    override fun checkClientTrusted(
                        chain: Array<X509Certificate>,
                        authType: String
                    ) {
                    }

                    @Throws(CertificateException::class)
                    override fun checkServerTrusted(
                        chain: Array<X509Certificate>,
                        authType: String
                    ) {
                    }

                    override fun getAcceptedIssuers(): Array<X509Certificate> {
                        return arrayOf()
                    }
                }
            )
            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, SecureRandom())
            val sslSocketFactory = sslContext.socketFactory
            builder.sslSocketFactory(sslSocketFactory, (trustAllCerts[0] as X509TrustManager))
            builder.hostnameVerifier(HostnameVerifier { _, _ -> true })
            builder.connectTimeout(5000, TimeUnit.MILLISECONDS)
            builder.readTimeout(60000, TimeUnit.MILLISECONDS)

        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }
}
