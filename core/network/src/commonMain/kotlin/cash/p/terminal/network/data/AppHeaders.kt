package cash.p.terminal.network.data

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders

fun HttpRequestBuilder.appHeaders(provider: AppHeadersProvider) {
    header("App-Version", provider.appVersion)
    header(HttpHeaders.AcceptLanguage, provider.currentLanguage)
    provider.appSignature?.let { header("App-Signature", it) }
}
