package cash.p.terminal.navigation

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ViewModelStoreOwnerForPageTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<String>()
    private val factory = viewModelFactory {
        initializer { SharedViewModel(events).also { events += "created" } }
    }
    private val owner = OwnerPage(factory)
    private val child = ChildPage(factory, events)
    private val backStack = NavBackStack<HSPage>(RootPage())
    private val navigation = HSNavigation(backStack)

    @Before
    fun setUp() {
        composeRule.setContent {
            NavDisplay(
                backStack = backStack,
                onBack = { navigation.navigateUp() },
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberSharedViewModelStoreNavEntryDecorator(),
                ),
                entryProvider = { key ->
                    NavEntry(key, key.contentKey(), key.metadata()) { key.GetContent(navigation) }
                },
            )
        }
        composeRule.runOnIdle { navigation.slideFromRight(owner) }
        composeRule.waitForIdle()
    }

    @Test
    fun viewModelStoreOwnerForPage_childAboveOwner_returnsOwnersViewModel() {
        composeRule.runOnIdle { navigation.slideFromRight(child) }
        composeRule.waitForIdle()

        assertSame(owner.viewModel, child.viewModels.last())
    }

    @Test
    fun viewModelStoreOwnerForPage_ownerPopped_clearsOwnersStore() {
        composeRule.runOnIdle { navigation.slideFromRight(child) }
        composeRule.waitForIdle()
        val shared = checkNotNull(owner.viewModel)
        assertFalse(shared.cleared)

        composeRule.runOnIdle { navigation.removeLastUntil(OwnerPage::class, inclusive = true) }
        composeRule.waitForIdle()

        assertTrue(shared.cleared)
    }

    @Test
    fun viewModelStoreOwnerForPage_ownerPoppedTogetherWithChild_keepsViewModelUntilChildLeaves() {
        composeRule.runOnIdle { navigation.slideFromRight(child) }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false

        composeRule.runOnIdle {
            child.compositionsAfterPop = 0
            navigation.removeLastUntil(OwnerPage::class, inclusive = true)
        }
        var frames = 0
        while (child.composed && frames++ < MAX_EXIT_FRAMES) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
        }

        assertFalse(child.composed)
        assertTrue(child.compositionsAfterPop > 0)
        assertEquals(listOf(owner.viewModel), child.viewModels.distinct())
        assertEquals(listOf("created", "child disposed", "cleared"), events)
    }

    class SharedViewModel(private val events: MutableList<String>) : ViewModel() {
        var cleared = false

        override fun onCleared() {
            cleared = true
            events += "cleared"
        }
    }

    private class OwnerPage(private val factory: ViewModelProvider.Factory) : HSPage() {
        var viewModel: SharedViewModel? = null

        @Composable
        override fun GetContent(navigation: HSNavigation) {
            val viewModel = viewModel<SharedViewModel>(factory = factory)
            SideEffect { this.viewModel = viewModel }
        }
    }

    private class ChildPage(
        private val factory: ViewModelProvider.Factory,
        private val events: MutableList<String>,
    ) : HSPage() {
        val viewModels = mutableListOf<SharedViewModel>()
        var compositionsAfterPop = 0
        var composed = false

        @Composable
        override fun GetContent(navigation: HSNavigation) {
            val viewModel = viewModel<SharedViewModel>(
                viewModelStoreOwner = navigation.viewModelStoreOwnerForPage(OwnerPage::class),
                factory = factory,
            )
            SideEffect {
                viewModels += viewModel
                compositionsAfterPop++
            }
            DisposableEffect(Unit) {
                composed = true
                onDispose {
                    composed = false
                    events += "child disposed"
                }
            }
        }
    }

    private companion object {
        const val MAX_EXIT_FRAMES = 120
    }
}
