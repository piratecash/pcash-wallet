package cash.p.terminal.navigation

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.result.ResultEventBus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class ResultLauncherTest : ComposeHostTest() {

    private val root = RootPage()
    private val backStack = NavBackStack<HSPage>(root)
    private val navigation = HSNavigation(backStack)
    private val caller = CallerPage()

    @Test
    fun rememberResultLauncher_callerCoveredByFullPage_keepsKeyInNewComposition() = runComposeUiTest {
        show()
        val first = PinPage()
        act { caller.launcher.slideFromRight(first, "a") }
        act { navigation.navigateUp() }
        val second = PinPage()
        act { caller.launcher.slideFromRight(second, "b") }

        assertEquals(2, caller.compositions)
        assertNotNull(first.resultKey)
        assertEquals(first.resultKey, second.resultKey)
    }

    @Test
    fun slideFromRight_sheetOnTop_recordsLaunchAndRemovesSheet() = runComposeUiTest {
        show()
        act { navigation.slideFromBottom(OptionsSheet()) }
        val pin = PinPage()

        act { caller.launcher.slideFromRight(pin, "arg") }

        assertEquals(listOf(root, caller, pin), backStack.toList())
        val key = checkNotNull(pin.resultKey)
        assertSame(caller, navigation.launcherOwners[key])
        assertSame(pin, navigation.launches.getValue(key).callee)
        assertEquals("arg", navigation.launches.getValue(key).arg)
    }

    @Test
    fun setResult_callerUnderFullPage_deliversIntoNewComposition() = runComposeUiTest {
        show()
        val pin = PinPage()
        act { caller.launcher.slideFromRight(pin, "arg") }

        act {
            navigation.setResult(pin, "ok")
            navigation.navigateUp()
        }

        assertEquals(2, caller.compositions)
        assertEquals(listOf("ok" to "arg"), caller.received)
        onNodeWithText("shown:ok").assertExists()
        assertTrue(navigation.launches.isEmpty())
    }

    @Test
    fun setResult_callerUnderSheet_deliversWhileSheetOpen() = runComposeUiTest {
        show()
        val sheet = RiskyAddressSheet()
        act { caller.launcher.slideFromBottom(sheet, "arg") }

        act { navigation.setResult(sheet, "picked") }

        assertEquals(listOf("picked" to "arg"), caller.received)
        assertEquals(listOf(root, caller, sheet), backStack.toList())
        assertEquals(1, caller.compositions)
    }

    @Test
    fun setResult_launchedWithArg_deliversPendingArg() = runComposeUiTest {
        show()
        val first = RiskyAddressSheet()
        val second = PinPage()
        act { caller.launcher.slideFromBottom(first, "first-arg") }
        act { navigation.setResult(first, "one") }
        act { caller.launcher.slideFromRight(second, "second-arg") }

        act {
            navigation.setResult(second, "two")
            navigation.navigateUp()
        }

        assertEquals(listOf("one" to "first-arg", "two" to "second-arg"), caller.received)
    }

    @Test
    fun setResult_earlierLaunchAnswersAfterLaterOneCancelled_isIgnored() = runComposeUiTest {
        show()
        val first = PinPage()
        act { caller.launcher.slideFromRight(first, "first-arg") }
        act { caller.launcher.slideFromRight(PinPage(), "second-arg") }
        act { navigation.navigateUp() }

        act {
            navigation.setResult(first, "ok")
            navigation.navigateUp()
        }

        assertTrue(caller.received.isEmpty())
    }

    @Test
    fun setResult_answerQueuedThenLauncherReused_isNotPairedWithNewArgument() = runComposeUiTest {
        show()
        val first = PinPage()
        act { caller.launcher.slideFromRight(first, "first-arg") }
        act { navigation.setResult(first, "ok") }
        act { caller.launcher.slideFromRight(PinPage(), "second-arg") }

        act {
            navigation.navigateUp()
            navigation.navigateUp()
        }

        assertTrue(caller.received.none { it.second == "second-arg" })
    }

    @Test
    fun setResult_thenNavigateUp_handlerNotInvokedSynchronously() = runComposeUiTest {
        show()
        val sheet = RiskyAddressSheet()
        act { caller.launcher.slideFromBottom(sheet, "arg") }

        runOnIdle {
            navigation.setResult(sheet, "picked")
            navigation.navigateUp()
            assertTrue(caller.received.isEmpty())
        }
        waitForIdle()

        assertEquals(listOf("picked" to "arg"), caller.received)
    }

    @Test
    fun setResult_calledTwice_firstResultWinsOnce() = runComposeUiTest {
        show()
        val pin = PinPage()
        act { caller.launcher.slideFromRight(pin, "arg") }

        act {
            navigation.setResult(pin, "first")
            navigation.setResult(pin, "second")
            navigation.navigateUp()
        }

        assertEquals(listOf("first" to "arg"), caller.received)
    }

    @Test
    fun setResult_calleeCoveredByAnotherPage_deliversAfterRemoveLastUntil() = runComposeUiTest {
        show()
        val pin = PinPage()
        act { caller.launcher.slideFromRight(pin, "arg") }
        act { navigation.slideFromRight(ConfirmPage()) }

        act { navigation.setResult(pin, "ok") }
        assertTrue(caller.received.isEmpty())
        act { navigation.removeLastUntil(PinPage::class, inclusive = true) }

        assertEquals(listOf("ok" to "arg"), caller.received)
    }

    @Test
    fun setResult_launchedFromSheet_deliversWhileCallerSheetOpen() = runComposeUiTest {
        val received = mutableListOf<Any>()
        val callerSheet = LauncherPage(bottomSheet = true) { received += it }
        show()
        act { navigation.slideFromBottom(callerSheet) }
        val sheet = RiskyAddressSheet()
        act { callerSheet.launcher.slideFromBottom(sheet) }

        act { navigation.setResult(sheet, "picked") }

        assertEquals(listOf<Any>("picked"), received)
        assertEquals(listOf(root, caller, callerSheet, sheet), backStack.toList())
    }

    @Test
    fun rememberResultLauncher_insideSheet_bindsLaunchToThatSheet() = runComposeUiTest {
        val received = mutableListOf<Any>()
        val callerSheet = LauncherPage(bottomSheet = true) { received += it }
        show()
        act { navigation.slideFromBottom(callerSheet) }
        val sheet = RiskyAddressSheet()
        act { callerSheet.launcher.slideFromBottom(sheet) }

        act { navigation.removeTrailingBottomSheets() }
        act { navigation.setResult(sheet, "picked") }

        assertTrue(received.isEmpty())
        assertTrue(navigation.launches.isEmpty())
    }

    @Test
    fun slideFromRightSafely_calledFromHandlerAfterCalleePop_navigates() = runComposeUiTest {
        ForwardNavigationGate.release()
        val confirm = ConfirmPage()
        val page = LauncherPage { navigation.slideFromRightSafely(confirm) }
        show(page)
        val pin = PinPage()
        act { page.launcher.slideFromRight(pin) }

        act {
            navigation.setResult(pin, true)
            navigation.navigateUp()
        }

        assertEquals(listOf(root, page, confirm), backStack.toList())
    }

    @Test
    fun setResult_pageNotOpenedForResult_isNoOp() = runComposeUiTest {
        show()
        val pin = PinPage()
        val confirm = ConfirmPage()
        act { caller.launcher.slideFromRight(pin, "arg") }
        act { navigation.slideFromRight(confirm) }

        act {
            navigation.setResult(confirm, "stray")
            navigation.navigateUp()
            navigation.navigateUp()
        }

        assertTrue(caller.received.isEmpty())
        assertEquals(setOf(pin.resultKey), navigation.launches.keys)
    }

    @Test
    fun setResult_callerRemovedBeforeDelivery_handlerNotInvoked() = runComposeUiTest {
        show()
        val sheet = RiskyAddressSheet()
        act { caller.launcher.slideFromBottom(sheet, "arg") }

        // Paused frames keep the removed caller composed, so its collector does receive the buffered result.
        mainClock.autoAdvance = false
        runOnIdle {
            navigation.setResult(sheet, "picked")
            navigation.removeLastUntil(CallerPage::class, inclusive = true)
        }
        waitForIdle()
        mainClock.autoAdvance = true
        waitForIdle()

        assertTrue(caller.received.isEmpty())
        assertTrue(navigation.launches.isEmpty())
        assertEquals(listOf(root), backStack.toList())
    }

    @Test
    fun removeLastUntil_callerRemovedBeforeSetResult_dropsLaunch() = runComposeUiTest {
        show()
        val pin = PinPage()
        act { caller.launcher.slideFromRight(pin, "arg") }

        act { navigation.removeLastUntil(CallerPage::class, inclusive = true) }
        assertTrue(navigation.launches.isEmpty())
        act { navigation.setResult(pin, "late") }

        assertTrue(caller.received.isEmpty())
    }

    @Test
    fun removeLastUntil_coveredCallerRemoved_closesItsResultChannels() = runComposeUiTest {
        show()
        val pin = PinPage()
        act { caller.launcher.slideFromRight(pin, "arg") }
        act {
            navigation.setResult(pin, "ok")
            navigation.navigateUp()
        }
        act { navigation.slideFromRight(ConfirmPage()) }

        act { navigation.removeLastUntil(CallerPage::class, inclusive = true) }

        assertTrue(navigation.resultBus.openResultKeys().isEmpty())
    }

    @Test
    fun rememberResultLauncher_outsideAnyPage_survivesBackStackChanges() = runComposeUiTest {
        var rootLauncher: ResultLauncher<Unit>? = null
        val received = mutableListOf<String>()
        setContent {
            rootLauncher = navigation.rememberResultLauncher<String> { received += it }
            WithBackDispatcher { TestNavDisplay(navigation) }
        }
        val pin = PinPage()
        act { checkNotNull(rootLauncher).slideFromRight(pin) }
        act { navigation.slideFromRight(ConfirmPage()) }
        act { navigation.removeLastUntil(PinPage::class, inclusive = false) }
        assertEquals(1, navigation.launches.size)

        act {
            navigation.setResult(pin, "done")
            navigation.navigateUp()
        }

        assertEquals(listOf("done"), received)
    }

    @Test
    fun rememberResultLauncher_insideBranchThatLeftComposition_resultIsLost() = runComposeUiTest {
        val page = BranchCallerPage()
        show(page)
        val sheet = RiskyAddressSheet()
        act { checkNotNull(page.launcher).slideFromBottom(sheet) }
        act { page.launcherComposed = false }

        act {
            navigation.setResult(sheet, "picked")
            navigation.navigateUp()
        }
        act { page.launcherComposed = true }

        assertTrue(page.received.isEmpty())
    }

    @Test
    fun handler_launchesAnotherLauncher_deliversNestedResult() = runComposeUiTest {
        val pin = PinPage()
        val page = NestedCallerPage(pin)
        show(page)
        act { page.first.slideFromBottom(RiskyAddressSheet()) }

        act { navigation.setResult(backStack.last(), "continue") }
        assertEquals(listOf(root, page, pin), backStack.toList())
        act {
            navigation.setResult(pin, true)
            navigation.navigateUp()
        }

        assertEquals(listOf<Any>("continue", true), page.received)
    }

    @Test
    fun rememberQrScannerLauncher_open_pushesScannerAndDeliversText() = runComposeUiTest {
        val page = QrCallerPage()
        show(page)

        act { page.scanner.open("Scan", showPasteButton = true) }
        val scanner = backStack.last() as ScannerPage
        assertEquals(QrScannerInput("Scan", showPasteButton = true), scanner.input)
        assertEquals(NavigationType.SlideFromBottom, scanner.navType)
        act {
            navigation.setResult(scanner, QrScannerResult("address"))
            navigation.navigateUp()
        }

        assertEquals(listOf("address"), page.received)
    }

    private fun ComposeUiTest.show(page: HSPage = caller) {
        setContent { WithBackDispatcher { TestNavDisplay(navigation) } }
        act { navigation.slideFromRight(page) }
    }

    private fun ComposeUiTest.act(action: () -> Unit) {
        runOnIdle(action)
        waitForIdle()
    }
}

// The bus does not expose its channels.
private fun ResultEventBus.openResultKeys(): Set<Any?> =
    (javaClass.getDeclaredField("channelMap").apply { isAccessible = true }.get(this) as Map<*, *>).keys

/** Shows the last result in state remembered by its current composition. */
private class CallerPage : HSPage() {
    lateinit var launcher: ResultLauncher<String>
    val received = mutableListOf<Pair<String, String>>()
    var compositions = 0
        private set

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        var shown by remember { mutableStateOf("none") }
        launcher = navigation.rememberResultLauncher<String, String> { result, arg ->
            received += result to arg
            shown = result
        }
        DisposableEffect(Unit) {
            compositions++
            onDispose { }
        }
        BasicText("shown:$shown")
    }
}

private class BranchCallerPage : HSPage() {
    var launcherComposed by mutableStateOf(true)
    var launcher: ResultLauncher<Unit>? = null
    val received = mutableListOf<String>()

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        if (launcherComposed) launcher = navigation.rememberResultLauncher<String> { received += it }
    }
}

private class NestedCallerPage(private val next: HSPage) : HSPage() {
    lateinit var first: ResultLauncher<Unit>
    val received = mutableListOf<Any>()

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val second = navigation.rememberResultLauncher<Boolean> { received += it }
        first = navigation.rememberResultLauncher<String> {
            received += it
            second.slideFromRight(next)
        }
    }
}

private class QrCallerPage : HSPage() {
    lateinit var scanner: QrScannerLauncher
    val received = mutableListOf<String>()

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        scanner = navigation.rememberQrScannerLauncher(ScannerAppPages) { received += it }
    }
}

private class ScannerPage(val input: QrScannerInput) : TestPage()

private object ScannerAppPages : AppPages {
    override fun coin(coinUid: String): HSPage = error("unused")
    override fun createAccount(): HSPage = error("unused")
    override fun restoreAccount(): HSPage = error("unused")
    override fun backupKey(input: BackupKeyInput): HSPage = error("unused")
    override fun qrScanner(input: QrScannerInput): HSPage = ScannerPage(input)
}
