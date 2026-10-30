package cash.p.terminal.modules.markdown

import cash.p.terminal.manager.IConnectivityManager
import cash.p.terminal.ui_compose.entities.ViewState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class MarkdownViewModelTest {

    private val isConnected = MutableStateFlow(true)
    private val networkAvailability = MutableSharedFlow<Boolean>()
    private val connectivityManager = mockk<IConnectivityManager> {
        every { this@mockk.isConnected } returns this@MarkdownViewModelTest.isConnected
        every { networkAvailabilityFlow } returns networkAvailability
    }

    private class FakeSource(var result: () -> String) : MarkdownSource {
        val requestedUrls = mutableListOf<String>()

        override suspend fun load(url: String): String {
            requestedUrls += url
            return result()
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_sourceReturnsContent_showsSuccessWithContent() = runTest {
        val source = FakeSource { "# Guide" }

        val viewModel = MarkdownViewModel("https://host/guide.md", source, connectivityManager)

        assertEquals(ViewState.Success, viewModel.viewState)
        assertEquals("# Guide", viewModel.markdownContent)
        assertEquals(listOf("https://host/guide.md"), source.requestedUrls)
    }

    @Test
    fun init_sourceThrows_showsError() = runTest {
        val source = FakeSource { error("404") }

        val viewModel = MarkdownViewModel("https://host/guide.md", source, connectivityManager)

        assertIs<ViewState.Error>(viewModel.viewState)
    }

    @Test
    fun networkAvailability_connectivityRegainedWhileError_retriesAndSucceeds() = runTest {
        val source = FakeSource { error("offline") }
        val viewModel = MarkdownViewModel("https://host/guide.md", source, connectivityManager)
        source.result = { "# Guide" }

        networkAvailability.emit(true)

        assertEquals(ViewState.Success, viewModel.viewState)
        assertEquals("# Guide", viewModel.markdownContent)
        assertEquals(2, source.requestedUrls.size)
    }

    @Test
    fun networkAvailability_notConnectedWhileError_doesNotRetry() = runTest {
        val source = FakeSource { error("offline") }
        val viewModel = MarkdownViewModel("https://host/guide.md", source, connectivityManager)
        isConnected.value = false

        networkAvailability.emit(false)

        assertIs<ViewState.Error>(viewModel.viewState)
        assertEquals(1, source.requestedUrls.size)
    }
}
