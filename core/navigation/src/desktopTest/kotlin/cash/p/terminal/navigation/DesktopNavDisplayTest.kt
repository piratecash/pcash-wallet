package cash.p.terminal.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.lifecycle.ViewModelStoreOwner
import androidx.navigation3.runtime.NavBackStack
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

@OptIn(ExperimentalTestApi::class)
class DesktopNavDisplayTest {

    private val backStack = NavBackStack<HSPage>(RootPage())
    private val navigation = HSNavigation(backStack)

    @Test
    fun navigateUp_afterPush_restoresPreviousPage() = runComposeUiTest {
        setContent { WithBackDispatcher { TestNavDisplay(navigation) } }

        runOnIdle { navigation.slideFromRight(SendPage()) }
        waitForIdle()
        assertEquals(listOf(RootPage::class, SendPage::class), backStack.map { it::class })

        runOnIdle { navigation.navigateUp() }
        waitForIdle()
        assertEquals(listOf(RootPage::class), backStack.map { it::class })
    }

    @Test
    fun setResult_calleeClosed_deliversResultToCaller() = runComposeUiTest {
        setContent { WithBackDispatcher { TestNavDisplay(navigation) } }
        val callee = SendPage()
        var received: String? = null

        runOnIdle { navigation.slideFromRightForResult<String>(callee) { received = it } }
        waitForIdle()
        runOnIdle {
            navigation.setResult(callee, "done")
            navigation.navigateUp()
        }
        waitForIdle()

        assertEquals("done", received)
    }

    @Test
    fun slideFromBottom_sheet_rendersContentAndDismissesOnPop() = runComposeUiTest {
        setContent { WithBackDispatcher { TestNavDisplay(navigation) } }

        runOnIdle { navigation.slideFromBottom(LabeledSheet("sheet content")) }
        waitForIdle()
        onNodeWithText("sheet content").assertExists()

        runOnIdle { navigation.navigateUp() }
        waitForIdle()
        onNodeWithText("sheet content").assertDoesNotExist()
    }

    @Test
    fun viewModelStoreOwnerForRoot_fromChildPage_returnsRootPageOwner() = runComposeUiTest {
        val rootOwners = mutableListOf<ViewModelStoreOwner>()
        val childOwners = mutableListOf<ViewModelStoreOwner>()
        backStack.clear()
        backStack.add(OwnerCapturingRootPage(rootOwners))
        setContent { WithBackDispatcher { TestNavDisplay(navigation) } }

        runOnIdle { navigation.slideFromRight(OwnerCapturingPage(childOwners)) }
        waitForIdle()

        assertSame(rootOwners.last().viewModelStore, childOwners.last().viewModelStore)
    }
}

// The test scene has no window host, which NavDisplay's back handling expects to find.
@Composable
private fun WithBackDispatcher(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalNavigationEventDispatcherOwner provides rememberNavigationEventDispatcherOwner(parent = null),
        content = content,
    )
}

private class LabeledSheet(private val label: String) : HSBottomSheet() {
    @Composable
    override fun GetContent(navigation: HSNavigation) {
        Text(label)
    }
}

private open class OwnerCapturingPage(private val owners: MutableList<ViewModelStoreOwner>) : HSPage() {
    @Composable
    override fun GetContent(navigation: HSNavigation) {
        owners += navigation.viewModelStoreOwnerForRoot()
    }
}

// A distinct class, as the lookup resolves the root by class.
private class OwnerCapturingRootPage(owners: MutableList<ViewModelStoreOwner>) : OwnerCapturingPage(owners)
