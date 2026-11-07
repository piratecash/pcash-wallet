package cash.p.terminal.modules.markdown

import io.horizontalsystems.core.DispatcherProvider
import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.withContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

class KtorMarkdownSource(
    private val httpClient: HttpClient,
    private val dispatcherProvider: DispatcherProvider,
) : MarkdownSource {
    override suspend fun load(url: String): String = withContext(dispatcherProvider.io) {
        httpClient.get(url) { expectSuccess = true }.bodyAsText()
    }
}

/** Lives here because the Ktor client is not on the `shared` module's classpath. */
val desktopMarkdownModule = module {
    singleOf(::KtorMarkdownSource) bind MarkdownSource::class
}
