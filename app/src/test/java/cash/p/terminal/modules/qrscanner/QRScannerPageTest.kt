package cash.p.terminal.modules.qrscanner

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.navigation3.runtime.NavBackStack
import cash.p.terminal.core.deeplink.DeeplinkParser
import cash.p.terminal.core.managers.TonConnectManager
import cash.p.terminal.modules.main.PlainTestPage
import cash.p.terminal.modules.main.TestNav3Host
import cash.p.terminal.modules.main.ScreenTestPage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.QrScannerInput
import cash.p.terminal.navigation.QrScannerResult
import cash.p.terminal.navigation.ResultLauncher
import cash.p.terminal.navigation.rememberResultLauncher
import cash.p.terminal.navigation.slideFromRight
import cash.p.terminal.tangem.domain.sdk.CardSdkConfigRepository
import io.horizontalsystems.core.IPinComponent
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class QRScannerPageTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var deliveredResult: QrScannerResult? = null
    private lateinit var launcher: ResultLauncher<Unit>
    private val root = PlainTestPage()
    private val caller = ScreenTestPage { navigation ->
        launcher = navigation.rememberResultLauncher<QrScannerResult> { deliveredResult = it }
    }
    private val scanner = QRScannerPage(QrScannerInput(title = ""))
    private val navigation = HSNavigation(NavBackStack<HSPage>(root, caller))
    private var scannerViewModel: QRScannerViewModel? = null

    @Before
    fun setUp() {
        val deeplinkParser = mockk<DeeplinkParser> { every { parse(any<String>()) } returns null }
        startKoin {
            modules(
                module {
                    viewModel { QRScannerViewModel(mockk()).also { scannerViewModel = it } }
                    single { deeplinkParser }
                    single { mockk<TonConnectManager>(relaxed = true) }
                    single { mockk<CardSdkConfigRepository>(relaxed = true) }
                    single { mockk<IPinComponent>(relaxed = true) }
                }
            )
        }
        composeRule.setContent {
            TestNav3Host(navigation)
        }
        composeRule.waitForIdle()
        composeRule.runOnUiThread { launcher.slideFromRight(scanner) }
        composeRule.waitForIdle()
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun handleScanResult_scannerOnTop_deliversResultAndClosesScannerOnly() {
        composeRule.runOnUiThread { checkNotNull(scannerViewModel).onTextPasted(ADDRESS) }
        composeRule.waitForIdle()

        assertEquals(QrScannerResult(ADDRESS), deliveredResult)
        assertEquals(listOf(root, caller), navigation.backStack.toList())
    }

    @Test
    fun handleScanResult_arrivesWhileScannerAnimatesOut_keepsCallerAndDropsResult() {
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnUiThread { navigation.navigateUp() }
        repeat(3) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
        }

        // A gallery decode finishing mid-exit: the scanner is still composed but no longer on top.
        composeRule.runOnUiThread { checkNotNull(scannerViewModel).onTextPasted(ADDRESS) }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        assertNull(deliveredResult)
        assertEquals(listOf(root, caller), navigation.backStack.toList())
    }

    @Test
    fun handleScanResult_arrivesWhileAppPaused_deliversAfterResume() {
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        composeRule.runOnUiThread { checkNotNull(scannerViewModel).onTextPasted(ADDRESS) }
        composeRule.waitForIdle()
        assertNull(deliveredResult)

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()

        assertEquals(QrScannerResult(ADDRESS), deliveredResult)
        assertEquals(listOf(root, caller), navigation.backStack.toList())
    }

    private companion object {
        const val ADDRESS = "0x0000000000000000000000000000000000000001"
    }
}
