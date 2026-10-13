package cash.p.terminal.modules.blockchainstatus

import android.net.Uri
import cash.p.terminal.core.TestDispatcherProvider
import io.horizontalsystems.core.CoreApp
import io.horizontalsystems.core.ISystemInfoManager
import io.horizontalsystems.core.logger.AppLog
import io.horizontalsystems.core.storage.LogsDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Robolectric is required for [android.os.Build.SUPPORTED_ABIS] and a real [android.content.pm.ApplicationInfo],
 * both read by reportBuildInfo() via App.instance/BuildInfo during init's recomputeStatusAsTextIfReady().
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestCoreApp::class)
class BlockchainStatusViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val provider = mockk<BlockchainStatusProvider>(relaxed = true) {
        every { logFilterTag } returns "Zcash"
    }
    private val systemInfoManager = mockk<ISystemInfoManager>(relaxed = true)
    private val statusReportFile = mockk<StatusReportFile>(relaxed = true)
    private val logsDao = mockk<LogsDao>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        AppLog.logsDao = logsDao
        CoreApp.instance = RuntimeEnvironment.getApplication() as CoreApp
        // Robolectric leaves this field unset; reportBuildInfo() requires a non-null value.
        CoreApp.instance.applicationInfo.nativeLibraryDir = "/data/app/x86_64"
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): BlockchainStatusViewModel = BlockchainStatusViewModel(
        provider = provider,
        dispatcherProvider = TestDispatcherProvider(dispatcher, TestScope(dispatcher)),
        systemInfoManager = systemInfoManager,
        statusReportFile = statusReportFile,
    )

    /** Init reaches reportBuildInfo/App.instance, but stays on the test dispatcher — no real-IO hop needed. */
    private fun BlockchainStatusViewModel.awaitInitLoaded() {
        runBlocking { withTimeout(30_000) { uiState.first { !it.loading } } }
    }

    @Test
    fun constructor_initCompletes_neverExportsOrStreamsTheFullLog() {
        val viewModel = createViewModel()

        viewModel.awaitInitLoaded()
        assertNotNull(viewModel.uiState.value.statusAsText)

        verify(exactly = 0) { logsDao.streamGrouped() }
        verify(exactly = 0) { logsDao.streamGroupedByTag(any()) }
        coVerify(exactly = 0) { statusReportFile.share(any(), any(), any(), any()) }
        coVerify(exactly = 0) { statusReportFile.write(any(), any(), any(), any()) }
    }

    @Test
    fun prepareShareUri_calledWhileFirstExportInProgress_returnsNullAndKeepsFlagUntilFirstCompletes() =
        runTest(dispatcher) {
        val viewModel = createViewModel()
        viewModel.awaitInitLoaded()

        val shareUri = mockk<Uri>()
        val resumeExport = CompletableDeferred<Uri>()
        coEvery { statusReportFile.share(any(), any(), any(), any()) } coAnswers { resumeExport.await() }

        val firstCall = async { viewModel.prepareShareUri() }
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.shareInProgress)

        val secondResult = viewModel.prepareShareUri()

        assertNull(secondResult)
        assertTrue(viewModel.uiState.value.shareInProgress)
        coVerify(exactly = 1) { statusReportFile.share(any(), any(), any(), any()) }

        resumeExport.complete(shareUri)
        assertEquals(shareUri, firstCall.await())
        assertFalse(viewModel.uiState.value.shareInProgress)
    }
}

internal class TestCoreApp : CoreApp() {
    override fun localizedContext() = this
    override val isSwapEnabled: Boolean = false
}
