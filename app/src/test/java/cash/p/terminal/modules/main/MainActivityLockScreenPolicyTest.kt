package cash.p.terminal.modules.main

import androidx.navigation3.runtime.NavBackStack
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.tangem.domain.sdk.CardSdkProvider
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

class MainActivityLockScreenPolicyTest {

    private fun assertLockDecision(
        expected: Boolean,
        hasSavedInstanceState: Boolean,
        pinSet: Boolean,
        currentTaskId: Int = 42,
        previouslyResumedTaskId: Int? = null,
    ) {
        assertEquals(
            expected,
            shouldLockOnCreate(
                hasSavedInstanceState = hasSavedInstanceState,
                pinSet = pinSet,
                currentTaskId = currentTaskId,
                previouslyResumedTaskId = previouslyResumedTaskId,
            )
        )
    }

    @Test
    fun shouldLockOnCreate_savedStateAbsentWithPinAndNoPreviousActivity_locks() {
        assertLockDecision(
            expected = true,
            hasSavedInstanceState = false,
            pinSet = true,
        )
    }

    @Test
    fun shouldLockOnCreate_sameTaskActivityAlreadyResumed_doesNotLock() {
        assertLockDecision(
            expected = false,
            hasSavedInstanceState = false,
            pinSet = true,
            currentTaskId = 42,
            previouslyResumedTaskId = 42,
        )
    }

    @Test
    fun shouldLockOnCreate_differentTaskActivityResumed_locks() {
        assertLockDecision(
            expected = true,
            hasSavedInstanceState = false,
            pinSet = true,
            currentTaskId = 42,
            previouslyResumedTaskId = 41,
        )
    }

    @Test
    fun shouldLockOnCreate_savedStatePresentWithPin_doesNotLock() {
        assertLockDecision(
            expected = false,
            hasSavedInstanceState = true,
            pinSet = true,
        )
    }

    @Test
    fun shouldLockOnCreate_savedStateAbsentWithoutPin_doesNotLock() {
        assertLockDecision(
            expected = false,
            hasSavedInstanceState = false,
            pinSet = false,
        )
    }

    @Test
    fun calculatorPauseProtection_externalActivityPrepared_securesSnapshot() {
        assertEquals(
            CalculatorPauseProtection.SecureSnapshot,
            calculatorPauseProtection(
                calculatorMode = true,
                pinSet = true,
                externalActivityLaunching = true,
            )
        )
    }

    @Test
    fun calculatorPauseProtection_regularPause_showsCalculator() {
        assertEquals(
            CalculatorPauseProtection.ShowCalculator,
            calculatorPauseProtection(
                calculatorMode = true,
                pinSet = true,
                externalActivityLaunching = false,
            )
        )
    }

    @Test
    fun calculatorPauseProtection_calculatorModeDisabled_doesNothing() {
        assertEquals(
            CalculatorPauseProtection.None,
            calculatorPauseProtection(
                calculatorMode = false,
                pinSet = true,
                externalActivityLaunching = true,
            )
        )
    }

    @Test
    fun closeWindowsAboveLockScreen_sheetsOnTop_removesOnlySheetsAndCancelsTangem() {
        val root = PlainTestPage()
        val page = PlainTestPage()
        val bottomPushedPage = PlainTestPage()
        val navigation = HSNavigation(NavBackStack<HSPage>(root))
        navigation.slideFromRight(page)
        navigation.slideFromBottom(bottomPushedPage)
        navigation.slideFromBottom(TestSheet())
        navigation.slideFromBottom(TestSheet())
        val cardSdkProvider = mockk<CardSdkProvider>(relaxed = true)

        closeWindowsAboveLockScreen(navigation, cardSdkProvider)

        assertEquals(listOf<HSPage>(root, page, bottomPushedPage), navigation.backStack.toList())
        verify { cardSdkProvider.cancelSession() }
    }
}
