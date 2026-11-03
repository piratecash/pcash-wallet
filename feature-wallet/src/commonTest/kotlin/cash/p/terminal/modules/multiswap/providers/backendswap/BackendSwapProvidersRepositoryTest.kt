package cash.p.terminal.modules.multiswap.providers.backendswap

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.modules.multiswap.providers.SwapProvidersStorage
import cash.p.terminal.network.backendswap.data.entity.BackendSwapError
import cash.p.terminal.network.backendswap.data.repository.BackendSwapRepository
import cash.p.terminal.network.backendswap.domain.entity.BackendSwapProviderInfo
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class BackendSwapProvidersRepositoryTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val backendSwapRepository = mockk<BackendSwapRepository>()
    private val storage = object : SwapProvidersStorage {
        override var disabledProviderIds: Set<String> = emptySet()
        override var backendProviders: String? = null
    }

    private val changelly = provider("changelly")

    @Test
    fun providers_listPersistedByEarlierRefresh_loadedAtConstruction() = runTest(dispatcher) {
        coEvery { backendSwapRepository.getProviders() } returns listOf(changelly)
        createRepository().refresh()

        assertEquals(listOf(changelly), createRepository().providers.value)
    }

    @Test
    fun refresh_success_publishesFetchedList() = runTest(dispatcher) {
        coEvery { backendSwapRepository.getProviders() } returns listOf(changelly)
        val repository = createRepository()

        repository.refresh()

        assertEquals(listOf(changelly), repository.providers.value)
    }

    @Test
    fun refresh_failure_keepsCachedList() = assertRefreshFailureKeepsCachedList(IOException("offline"))

    @Test
    fun refresh_backendError_keepsCachedList() =
        assertRefreshFailureKeepsCachedList(BackendSwapError(statusCode = 500, message = "Internal Server Error"))

    private fun assertRefreshFailureKeepsCachedList(failure: Throwable) = runTest(dispatcher) {
        coEvery { backendSwapRepository.getProviders() } returns listOf(changelly) andThenThrows failure
        val repository = createRepository()
        repository.refresh()

        repository.refresh()

        assertEquals(listOf(changelly), repository.providers.value)
        assertEquals(listOf(changelly), createRepository().providers.value)
    }

    @Test
    fun refresh_inactiveTypelessOrFixedOnlyProviders_dropped() = runTest(dispatcher) {
        coEvery { backendSwapRepository.getProviders() } returns listOf(
            changelly,
            provider("inactive", active = false),
            provider("typeless", supportsFloat = false),
            provider("fixedonly", supportsFixed = true, supportsFloat = false),
        )
        val repository = createRepository()

        repository.refresh()

        assertEquals(listOf("changelly"), repository.providers.value.map { it.name })
    }

    @Test
    fun providers_fixedOnlyProviderCachedByOlderBuild_notLoaded() = runTest(dispatcher) {
        storage.backendProviders =
            """[{"name":"fixedonly","displayName":"Fixedonly","supportsFixed":true,"supportsFloat":false}]"""

        assertEquals(emptyList<BackendSwapProviderInfo>(), createRepository().providers.value)
    }

    private fun createRepository() = BackendSwapProvidersRepository(
        backendSwapRepository = backendSwapRepository,
        storage = storage,
        dispatcherProvider = TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher)),
    )

    private fun provider(
        name: String,
        active: Boolean = true,
        supportsFixed: Boolean = false,
        supportsFloat: Boolean = true,
    ) = BackendSwapProviderInfo(
        name = name,
        displayName = name.replaceFirstChar { it.titlecase() },
        logoUrl = "https://p.cash/$name.png",
        active = active,
        supportsFixed = supportsFixed,
        supportsFloat = supportsFloat,
    )
}
