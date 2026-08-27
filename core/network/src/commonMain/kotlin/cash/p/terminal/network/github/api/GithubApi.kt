package cash.p.terminal.network.github.api

import cash.p.terminal.network.github.data.GithubApiConfig
import cash.p.terminal.network.github.data.entity.GithubContentDto
import cash.p.terminal.network.github.data.entity.GithubReleaseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException

internal class GithubApi(
    private val httpClient: HttpClient,
    private val config: GithubApiConfig,
) {
    suspend fun getLatestRelease(): GithubReleaseDto {
        val response = requestWithFallback(
            primaryUrl = "${config.apiBaseUrl}/releases/latest",
            proxyUrl = "${config.apiProxyBaseUrl}/releases/latest",
            accept = GITHUB_ACCEPT,
        )
        if (!response.status.isSuccess()) throw GithubApiException(response.status.value)
        return response.body()
    }

    /** Lists the entries of a repo folder via the contents API (e.g. "release-notes/en"). */
    suspend fun getFolderContents(folder: String): List<GithubContentDto> {
        val response = requestWithFallback(
            primaryUrl = "${config.apiBaseUrl}/contents/$folder",
            proxyUrl = "${config.apiProxyBaseUrl}/contents/$folder",
            accept = GITHUB_ACCEPT,
        )
        if (!response.status.isSuccess()) throw GithubApiException(response.status.value)
        return response.body()
    }

    /**
     * Raw markdown file by its repo-root-relative [path] (e.g. "release-notes/en/0.57.x.md").
     * Returns null when the file genuinely does not exist (404) so callers can fall back.
     */
    suspend fun getRawFile(path: String, ref: String? = null): String? {
        val response = if (ref == null) {
            requestWithFallback(
                primaryUrl = "${config.rawBaseUrl}/$path",
                proxyUrl = "${config.rawProxyBaseUrl}/$path",
                accept = null,
            )
        } else {
            requestWithFallback(
                primaryUrl = "${config.apiBaseUrl}/contents/$path",
                proxyUrl = "${config.apiProxyBaseUrl}/contents/$path",
                accept = GITHUB_RAW_ACCEPT,
                queryParameters = mapOf(REF_PARAMETER to ref),
            )
        }
        return when {
            response.status.isSuccess() -> response.bodyAsText()
            response.status == HttpStatusCode.NotFound -> null
            else -> throw GithubApiException(response.status.value)
        }
    }

    private suspend fun requestWithFallback(
        primaryUrl: String,
        proxyUrl: String,
        accept: String?,
        queryParameters: Map<String, String> = emptyMap(),
    ): HttpResponse {
        val primary = tryRequest(primaryUrl, accept, queryParameters)
        if (primary != null && !primary.shouldFallback()) return primary
        return tryRequest(proxyUrl, accept, queryParameters) ?: primary ?: throw GithubApiException(null)
    }

    private suspend fun tryRequest(
        requestUrl: String,
        accept: String?,
        queryParameters: Map<String, String>,
    ): HttpResponse? =
        try {
            httpClient.get {
                url(requestUrl)
                if (accept != null) header(HttpHeaders.Accept, accept)
                queryParameters.forEach { (name, value) -> parameter(name, value) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    private fun HttpResponse.shouldFallback(): Boolean =
        status == HttpStatusCode.Forbidden ||
            status == HttpStatusCode.RequestTimeout ||
            status == HttpStatusCode.TooManyRequests ||
            status.value >= HttpStatusCode.InternalServerError.value

    private companion object {
        const val GITHUB_ACCEPT = "application/vnd.github+json"
        const val GITHUB_RAW_ACCEPT = "application/vnd.github.raw+json"
        const val REF_PARAMETER = "ref"
    }
}

class GithubApiException(val statusCode: Int?) :
    Exception("GitHub request failed: status=$statusCode")
