package cash.p.terminal.modules.blockchainstatus

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.adapters.zcash.CapturedAppLog
import io.horizontalsystems.core.ISystemInfoManager
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

class BlockchainStatusViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val provider = mockk<BlockchainStatusProvider>(relaxed = true) {
        every { logFilterTag } returns "Zcash"
    }
    private val systemInfoManager = mockk<ISystemInfoManager>(relaxed = true)
    private val buildInfo = mockk<StatusReportBuildInfo>(relaxed = true) {
        every { entries() } returns linkedMapOf("Report Time" to formatReportTime(0L))
    }
    private lateinit var appLog: CapturedAppLog

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        appLog = CapturedAppLog()
    }

    @After
    fun tearDown() {
        appLog.close()
        Dispatchers.resetMain()
    }

    private fun createViewModel(): BlockchainStatusViewModel = BlockchainStatusViewModel(
        provider = provider,
        dispatcherProvider = TestDispatcherProvider(dispatcher, TestScope(dispatcher)),
        systemInfoManager = systemInfoManager,
        buildInfo = buildInfo,
    )

    /** Init stays on the test dispatcher — no real-IO hop needed. */
    private fun BlockchainStatusViewModel.awaitInitLoaded() {
        runBlocking { withTimeout(30_000) { uiState.first { !it.loading } } }
    }

    @Test
    fun constructor_initCompletes_neverExportsOrStreamsTheFullLog() {
        val viewModel = createViewModel()

        viewModel.awaitInitLoaded()
        assertNotNull(viewModel.uiState.value.statusAsText)

        verify(exactly = 0) { appLog.dao.getGroupedPage(any(), any(), any()) }
        verify(exactly = 0) { appLog.dao.getGroupedPageByTag(any(), any(), any(), any()) }
    }

    @Test
    fun constructor_initCompletes_statusTextHasReportTimeRightAfterTitle() {
        val viewModel = createViewModel()

        viewModel.awaitInitLoaded()

        val line = viewModel.uiState.value.statusAsText.orEmpty().lines()[1]
        assertTrue(line, line.startsWith("Report Time: ") && line.endsWith(" UTC"))
    }

    @Test
    fun formatReportTime_fixedInstant_formatsUtc() {
        assertEquals("2026-10-15 08:11:02 UTC", formatReportTime(1_792_051_862_000L))
    }

    @Test
    fun formatReportTime_nonUtcDefaultZoneNearMidnight_stillFormatsUtc() {
        val original = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
        try {
            assertEquals("2026-10-15 23:59:59 UTC", formatReportTime(1_792_108_799_000L))
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun share_calledWhileFirstExportInProgress_skipsAndKeepsFlagUntilFirstCompletes() =
        runTest(dispatcher) {
        val viewModel = createViewModel()
        viewModel.awaitInitLoaded()

        val resumeExport = CompletableDeferred<Unit>()
        var exports = 0
        val export: suspend (StatusReport) -> Unit = {
            exports++
            resumeExport.await()
        }

        val firstCall = async { viewModel.share(export) }
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.shareInProgress)

        viewModel.share(export)

        assertTrue(viewModel.uiState.value.shareInProgress)
        assertEquals(1, exports)

        resumeExport.complete(Unit)
        firstCall.await()
        assertFalse(viewModel.uiState.value.shareInProgress)
    }
}
