package cash.p.terminal.ui.compose.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigation3.runtime.NavBackStack
import cash.p.terminal.navigation.AppPages
import cash.p.terminal.navigation.BackupKeyInput
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.QrScannerInput
import cash.p.terminal.navigation.QrScannerResult
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class FormsQrScannerDesktopTest {

    private val received = mutableListOf<String>()

    @BeforeTest
    fun setUp() {
        startKoin { modules(module { single<AppPages> { ScannerAppPages } }) }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun formsInput_scanResultAfterCallerDisposed_showsScannedText() = runComposeUiTest {
        assertScanShown { navigation ->
            FormsInput(
                hint = "hint",
                qrScannerEnabled = true,
                qrScannerTitle = "scan",
                navigation = navigation,
                pasteEnabled = false,
                onValueChange = { received += it },
            )
        }
    }

    @Test
    fun formsInputMultiline_scanResultAfterCallerDisposed_showsScannedText() = runComposeUiTest {
        assertScanShown { navigation ->
            FormsInputMultiline(
                hint = "hint",
                pasteEnabled = false,
                qrScannerEnabled = true,
                qrScannerTitle = "scan",
                navigation = navigation,
                onValueChange = { received += it },
            )
        }
    }

    private fun ComposeUiTest.assertScanShown(form: @Composable (HSNavigation) -> Unit) {
        val navigation = HSNavigation(NavBackStack<HSPage>(FormPage(form)))
        setContent { ComposeAppTheme { TopPageHost(navigation) } }

        onNode(hasClickAction() and !hasSetTextAction()).performClick()
        waitUntil { navigation.backStack.size == 2 }
        val scanner = navigation.backStack.last()
        runOnIdle {
            navigation.setResult(scanner, QrScannerResult(SCANNED))
            navigation.navigateUp()
        }

        onNodeWithText(SCANNED).assertExists()
        assertEquals(listOf(SCANNED), received)
    }

    private class FormPage(private val form: @Composable (HSNavigation) -> Unit) : HSPage() {
        @Composable
        override fun GetContent(navigation: HSNavigation) = form(navigation)
    }

    private class ScannerPage : HSPage() {
        @Composable
        override fun GetContent(navigation: HSNavigation) = Unit
    }

    private object ScannerAppPages : AppPages {
        override fun coin(coinUid: String): HSPage = error("unused")
        override fun createAccount(): HSPage = error("unused")
        override fun restoreAccount(): HSPage = error("unused")
        override fun backupKey(input: BackupKeyInput): HSPage = error("unused")
        override fun qrScanner(input: QrScannerInput): HSPage = ScannerPage()
    }

    private companion object {
        const val SCANNED = "0xscanned"
    }
}

// Like NavDisplay, only the top page is composed, so a covered caller is disposed and restored later.
@Composable
private fun TopPageHost(navigation: HSNavigation) {
    val holder = rememberSaveableStateHolder()
    val top = navigation.backStack.last()
    holder.SaveableStateProvider(top.contentKey()) { top.Content(navigation) }
}
