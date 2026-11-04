package cash.p.terminal.navigation

import androidx.navigation3.runtime.NavBackStack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HSNavigationTest {

    private val root = RootPage()
    private val send = SendPage()
    private val backStack = NavBackStack<HSPage>(root)
    private val navigation = HSNavigation(backStack)

    @Before
    fun setUp() {
        BackNavigationGate.release()
        ForwardNavigationGate.release()
    }

    @Test
    fun navigateUp_rootOnly_keepsRootAndReturnsFalse() {
        navigation.slideFromRight(send)

        assertTrue(navigation.navigateUp())
        assertFalse(navigation.navigateUp())
        assertStack(root)
    }

    @Test
    fun removeLastUntil_inclusive_removesTargetAndEntriesAbove() {
        val pin = PinPage()
        push(send, pin, ConfirmPage())

        assertTrue(navigation.removeLastUntil(PinPage::class, inclusive = true))
        assertStack(root, send)
    }

    @Test
    fun removeLastUntil_exclusive_keepsLastTargetOccurrence() {
        val pin = PinPage()
        val secondSend = SendPage()
        push(send, pin, secondSend, ConfirmPage())

        assertTrue(navigation.removeLastUntil(SendPage::class, inclusive = false))
        assertStack(root, send, pin, secondSend)
    }

    @Test
    fun removeLastUntil_absent_returnsFalseWithoutMutation() {
        push(send)

        assertFalse(navigation.removeLastUntil(PinPage::class, inclusive = true))
        assertStack(root, send)
    }

    @Test
    fun slideFromRight_sheetsOnTop_removesTrailingSheets() {
        val pin = PinPage()
        push(send, OptionsSheet(), RiskyAddressSheet())

        navigation.slideFromRight(pin)

        assertStack(root, send, pin)
    }

    @Test
    fun slideFromBottom_pageOverSheets_removesTrailingSheets() {
        val pin = PinPage()
        push(send, OptionsSheet())

        navigation.slideFromBottom(pin)

        assertStack(root, send, pin)
    }

    @Test
    fun slideFromBottom_sheetOverSheet_keepsSheetsBeneath() {
        val options = OptionsSheet()
        val risky = RiskyAddressSheet()
        push(send, options)

        navigation.slideFromBottom(risky)

        assertStack(root, send, options, risky)
    }

    @Test
    fun removeTrailingBottomSheets_sheetsOverPages_removesOnlySheets() {
        val pin = PinPage()
        push(send, pin, OptionsSheet(), RiskyAddressSheet())

        navigation.removeTrailingBottomSheets()

        assertStack(root, send, pin)
    }

    @Test
    fun navigateUpSafely_secondCallWithinCooldown_isRejected() {
        push(send, PinPage())
        resumeTop()

        assertTrue(navigation.navigateUpSafely())
        resumeTop()
        assertFalse(navigation.navigateUpSafely())
        assertStack(root, send)

        BackNavigationGate.release()
        assertTrue(navigation.navigateUpSafely())
        assertStack(root)
    }

    @Test
    fun navigateUpSafely_topNotResumed_isRefusedWithoutTakingGate() {
        push(send)

        assertFalse(navigation.navigateUpSafely())
        assertStack(root, send)

        resumeTop()
        assertTrue(navigation.navigateUpSafely())
        assertStack(root)
    }

    @Test
    fun navigateUpSafely_topChangedAfterResume_isRefused() {
        val pin = PinPage()
        push(send)
        resumeTop()
        navigation.slideFromRight(pin)

        assertFalse(navigation.navigateUpSafely())
        assertStack(root, send, pin)
    }

    @Test
    fun navigateUpSafely_topPaused_isRefused() {
        push(send)
        resumeTop()
        navigation.onPagePaused(send)

        assertFalse(navigation.navigateUpSafely())
        assertStack(root, send)
    }

    @Test
    fun removeLastUntilSafely_secondCallWithinCooldown_isRejected() {
        push(send, PinPage(), SendPage(), PinPage())
        resumeTop()

        assertTrue(navigation.removeLastUntilSafely(PinPage::class, inclusive = true))
        resumeTop()
        assertFalse(navigation.removeLastUntilSafely(PinPage::class, inclusive = true))
        assertEquals(4, backStack.size)

        BackNavigationGate.release()
        assertTrue(navigation.removeLastUntilSafely(PinPage::class, inclusive = true))
        assertStack(root, send)
    }

    @Test
    fun removeLastUntilSafely_topNotResumed_isRefusedWithoutTakingGate() {
        val pin = PinPage()
        push(send, pin)

        assertFalse(navigation.removeLastUntilSafely(PinPage::class, inclusive = true))
        assertStack(root, send, pin)

        resumeTop()
        assertTrue(navigation.removeLastUntilSafely(PinPage::class, inclusive = true))
        assertStack(root, send)
    }

    @Test
    fun slideFromRightSafely_secondCallWithinCooldown_isRejected() {
        val pin = PinPage()

        assertTrue(navigation.slideFromRightSafely(send))
        assertFalse(navigation.slideFromRightSafely(ConfirmPage()))
        assertStack(root, send)

        ForwardNavigationGate.release()
        assertTrue(navigation.slideFromRightSafely(pin))
        assertStack(root, send, pin)
    }

    @Test
    fun navigateUpFrom_pageOnTop_removesIt() {
        val pin = PinPage()
        push(send, pin)

        assertTrue(navigation.navigateUpFrom(pin))
        assertStack(root, send)
    }

    @Test
    fun navigateUpFrom_pageAlreadyPopped_changesNothing() {
        val pin = PinPage()
        push(send, pin)
        navigation.navigateUp()

        assertFalse(navigation.navigateUpFrom(pin))
        assertStack(root, send)
    }

    @Test
    fun navigateUpFrom_anotherPageOnTop_changesNothing() {
        val pin = PinPage()
        val confirm = ConfirmPage()
        push(send, pin, confirm)

        assertFalse(navigation.navigateUpFrom(pin))
        assertStack(root, send, pin, confirm)
    }

    @Test
    fun contentKey_twoInstancesOfSamePageClass_areDistinct() {
        assertNotEquals(SendPage().contentKey(), SendPage().contentKey())
    }

    private fun push(vararg pages: HSPage) {
        pages.forEach { page ->
            if (page.bottomSheet) navigation.slideFromBottom(page) else navigation.slideFromRight(page)
        }
    }

    private fun resumeTop() = navigation.onPageResumed(backStack.last())

    private fun assertStack(vararg expected: HSPage) {
        assertEquals(expected.toList(), backStack.toList())
    }
}
