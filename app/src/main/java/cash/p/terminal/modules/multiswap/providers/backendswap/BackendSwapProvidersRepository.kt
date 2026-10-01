package cash.p.terminal.modules.multiswap.providers.backendswap

import android.content.SharedPreferences
import androidx.core.content.edit
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.network.backendswap.data.repository.BackendSwapRepository
import cash.p.terminal.network.backendswap.domain.entity.BackendSwapProviderInfo
import co.touchlab.kermit.Logger
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Last known active p.cash backend providers; persisted so pending swaps and history resolve after a restart. */
class BackendSwapProvidersRepository(
    private val backendSwapRepository: BackendSwapRepository,
    private val preferences: SharedPreferences,
    private val dispatcherProvider: DispatcherProvider,
) {
    private val _providers = MutableStateFlow(load())
    val providers: StateFlow<List<BackendSwapProviderInfo>> = _providers.asStateFlow()

    /** Keeps the cached list when the backend is slow or unreachable. */
    suspend fun refresh() {
        val fetched = try {
            withTimeoutOrNull(REFRESH_TIMEOUT_MS) {
                withContext(dispatcherProvider.io) { backendSwapRepository.getProviders() }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w(e) { "Backend swap providers refresh failed" }
            null
        } ?: return
        // Fixed-rate orders need a guaranteed minimum on the confirm screen, which is not implemented yet.
        val (usable, unsupported) = fetched.filter { it.active }.partition { it.supportsFloat }
        if (unsupported.isNotEmpty()) {
            logger.w { "Hidden backend swap providers without float rate support: ${unsupported.map { it.name }}" }
        }
        _providers.value = usable
        withContext(dispatcherProvider.io) {
            preferences.edit { putString(KEY_PROVIDERS, json.encodeToString(usable.map(StoredProvider::from))) }
        }
    }

    private fun load(): List<BackendSwapProviderInfo> =
        preferences.getString(KEY_PROVIDERS, null)
            ?.let { tryOrNull { json.decodeFromString<List<StoredProvider>>(it) } }
            ?.map(StoredProvider::toInfo)
            ?.filter { it.supportsFloat }
            .orEmpty()

    @Serializable
    private data class StoredProvider(
        val name: String,
        val displayName: String,
        val logoUrl: String? = null,
        val supportsFixed: Boolean = false,
        val supportsFloat: Boolean = false,
    ) {
        fun toInfo() = BackendSwapProviderInfo(
            name = name,
            displayName = displayName,
            logoUrl = logoUrl,
            active = true,
            supportsFixed = supportsFixed,
            supportsFloat = supportsFloat,
        )

        companion object {
            fun from(info: BackendSwapProviderInfo) = StoredProvider(
                name = info.name,
                displayName = info.displayName,
                logoUrl = info.logoUrl,
                supportsFixed = info.supportsFixed,
                supportsFloat = info.supportsFloat,
            )
        }
    }

    private companion object {
        const val KEY_PROVIDERS = "backend_swap_providers"
        const val REFRESH_TIMEOUT_MS = 5_000L
        val logger = Logger.withTag("BackendSwapProviders")
        val json = Json { ignoreUnknownKeys = true }
    }
}
