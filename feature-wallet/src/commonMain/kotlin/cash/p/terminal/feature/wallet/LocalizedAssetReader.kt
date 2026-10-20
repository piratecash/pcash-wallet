package cash.p.terminal.feature.wallet

import cash.p.terminal.core.tryOrNull
import cash.p.terminal.feature.wallet.resources.Res
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.withContext
import java.util.Locale

/** Reads `files/<prefix><language>.md`, falling back to English; empty when neither exists. */
class LocalizedAssetReader(private val dispatcherProvider: DispatcherProvider) {

    suspend fun read(prefix: String, language: String = Locale.getDefault().language): String =
        withContext(dispatcherProvider.io) {
            readOrNull(prefix, language) ?: readOrNull(prefix, FALLBACK_LANGUAGE).orEmpty()
        }

    private suspend fun readOrNull(prefix: String, language: String): String? =
        tryOrNull { Res.readBytes("files/$prefix$language.md").decodeToString() }

    private companion object {
        const val FALLBACK_LANGUAGE = "en"
    }
}
