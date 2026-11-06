package cash.p.terminal.shared.desktop.qrscanner

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.runtime.NavBackStack
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.modules.qrscanner.QRScannerViewModel
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.QrScannerInput
import cash.p.terminal.navigation.QrScannerResult
import cash.p.terminal.navigation.ResultLauncher
import cash.p.terminal.navigation.rememberResultLauncher
import cash.p.terminal.navigation.slideFromRight
import cash.p.terminal.shared.main.DesktopHudMessenger
import cash.p.terminal.shared.main.DesktopNavHost
import cash.p.terminal.ui_compose.components.UrlOpener
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class DesktopQrScannerPageTest {

    private val host = TestHost()
    private var deliveredResult: QrScannerResult? = null
    private lateinit var launcher: ResultLauncher<Unit>
    private val root = PlainPage()
    private val caller = CallerPage { navigation ->
        launcher = navigation.rememberResultLauncher<QrScannerResult> { deliveredResult = it }
    }
    private val navigation = HSNavigation(NavBackStack<HSPage>(root, caller))
    private var scannerViewModel: QRScannerViewModel? = null

    @BeforeTest
    fun setUp() {
        val session = mockk<DesktopWebcamSession>(relaxed = true) {
            coEvery { open(any(), any()) } returns OpenResult.NoDevices
        }
        val dispatchers = TestDispatcherProvider(Dispatchers.Unconfined, MainScope())
        startKoin {
            modules(
                module {
                    viewModel { QRScannerViewModel(mockk()).also { scannerViewModel = it } }
                    viewModel { DesktopQrScannerViewModel(session, mockk(relaxed = true), dispatchers) }
                }
            )
        }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun handleScanResult_scannerOnTop_deliversResultAndClosesScannerOnly() = runComposeUiTest {
        openScanner()

        runOnIdle { checkNotNull(scannerViewModel).onTextPasted(ADDRESS) }
        waitForIdle()

        assertEquals(QrScannerResult(ADDRESS), deliveredResult)
        assertEquals(listOf(root, caller), navigation.backStack.toList())
    }

    @Test
    fun handleScanResult_arrivesWhileScannerAnimatesOut_keepsCallerAndDropsResult() = runComposeUiTest {
        openScanner()
        mainClock.autoAdvance = false
        runOnIdle { navigation.navigateUp() }
        repeat(3) {
            mainClock.advanceTimeByFrame()
            waitForIdle()
        }

        // A late image decode finishing mid-exit: the scanner is still composed but no longer on top.
        runOnIdle { checkNotNull(scannerViewModel).onTextPasted(ADDRESS) }
        mainClock.autoAdvance = true
        waitForIdle()

        assertNull(deliveredResult)
        assertEquals(listOf(root, caller), navigation.backStack.toList())
    }

    @Test
    fun handleScanResult_arrivesWhileAppPaused_deliversAfterResume() = runComposeUiTest {
        openScanner()
        runOnIdle { host.lifecycle.currentState = Lifecycle.State.STARTED }
        runOnIdle { checkNotNull(scannerViewModel).onTextPasted(ADDRESS) }
        waitForIdle()
        assertNull(deliveredResult)

        runOnIdle { host.lifecycle.currentState = Lifecycle.State.RESUMED }
        waitForIdle()

        assertEquals(QrScannerResult(ADDRESS), deliveredResult)
        assertEquals(listOf(root, caller), navigation.backStack.toList())
    }

    private fun ComposeUiTest.openScanner() {
        setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides host) {
                ComposeAppTheme { DesktopNavHost(navigation, DesktopHudMessenger(), NoUrlOpener) }
            }
        }
        waitForIdle()
        runOnIdle { launcher.slideFromRight(DesktopQrScannerPage(QrScannerInput(title = ""))) }
        waitUntil(timeoutMillis = TIMEOUT_MS) { navigation.backStack.size == 3 && scannerViewModel != null }
    }

    /** The window: pausing it pauses every page, as minimising the desktop app does. */
    private class TestHost : LifecycleOwner {
        override val lifecycle = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
    }

    private class PlainPage : HSPage() {
        @Composable
        override fun GetContent(navigation: HSNavigation) = Unit
    }

    private class CallerPage(private val content: @Composable (HSNavigation) -> Unit) : HSPage() {
        @Composable
        override fun GetContent(navigation: HSNavigation) = content(navigation)
    }

    private object NoUrlOpener : UrlOpener {
        override fun open(url: String) = Unit
    }

    private companion object {
        const val ADDRESS = "0x0000000000000000000000000000000000000001"
        const val TIMEOUT_MS = 5_000L
    }
}
