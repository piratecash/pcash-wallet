package cash.p.terminal.modules.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityLockScreenPolicyTest {

    @Test
    fun shouldLockOnCreate_savedStateAbsentWithPin_locks() {
        assertTrue(
            shouldLockOnCreate(
                hasSavedInstanceState = false,
                pinSet = true,
            )
        )
    }

    @Test
    fun shouldLockOnCreate_savedStatePresentWithPin_doesNotLock() {
        assertFalse(
            shouldLockOnCreate(
                hasSavedInstanceState = true,
                pinSet = true,
            )
        )
    }

    @Test
    fun shouldLockOnCreate_savedStateAbsentWithoutPin_doesNotLock() {
        assertFalse(
            shouldLockOnCreate(
                hasSavedInstanceState = false,
                pinSet = false,
            )
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
}
