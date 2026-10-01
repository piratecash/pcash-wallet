package cash.p.terminal.modules.multiswap.providers.backendswap

import android.content.SharedPreferences
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.network.backendswap.data.repository.BackendSwapRepository
import cash.p.terminal.network.backendswap.domain.entity.BackendSwapProviderInfo
import io.mockk.coEvery
import io.mockk.every
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
    private val stored = mutableMapOf<String, String?>()
    private val preferences = mockk<SharedPreferences> {
        every { getString(any(), any()) } answers { stored[firstArg()] ?: secondArg() }
        every { edit() } returns mockk(relaxed = true) {
            every { putString(any(), any()) } answers {
                stored[firstArg()] = secondArg()
                self as SharedPreferences.Editor
            }
        }
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
    fun refresh_failure_keepsCachedList() = runTest(dispatcher) {
        coEvery { backendSwapRepository.getProviders() } returns listOf(changelly) andThenThrows
            IOException("offline")
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
        stored["backend_swap_providers"] =
            """[{"name":"fixedonly","displayName":"Fixedonly","supportsFixed":true,"supportsFloat":false}]"""

        assertEquals(emptyList<BackendSwapProviderInfo>(), createRepository().providers.value)
    }

    private fun createRepository() = BackendSwapProvidersRepository(
        backendSwapRepository = backendSwapRepository,
        preferences = preferences,
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
