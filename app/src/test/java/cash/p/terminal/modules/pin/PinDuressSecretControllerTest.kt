package cash.p.terminal.modules.pin

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.modules.pin.hiddenwallet.HiddenWalletPinPolicy
import cash.p.terminal.wallet.managers.UserManager
import io.horizontalsystems.core.IPinComponent
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinDuressSecretControllerTest {

    private val dispatcher = StandardTestDispatcher()
    private val pinComponent = mockk<IPinComponent>(relaxed = true)
    private val userManager = mockk<UserManager>(relaxed = true)
    private val hiddenWalletPinPolicy = mockk<HiddenWalletPinPolicy>()
    private val controller = PinDuressSecretController(
        pinComponent, userManager, hiddenWalletPinPolicy,
        TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher)),
    )

    @Test
    fun setDuressSecret_uniquePin_storesItAndWipesTheSecret() = runTest(dispatcher) {
        every { pinComponent.getDuressLevel() } returns 1
        every { pinComponent.isUnique("123456", true) } returns true
        val secret = "123456".toCharArray()

        assertEquals(LevelOutcome.SUCCESS, controller.setDuressSecret(secret))

        verify { pinComponent.setDuressPin("123456") }
        assertArrayEquals(CharArray(6), secret)
    }

    @Test
    fun setDuressSecret_pinOfAnotherLevel_isInUseAndStoresNothing() = runTest(dispatcher) {
        every { pinComponent.getDuressLevel() } returns 1
        every { pinComponent.isUnique("123456", true) } returns false

        assertEquals(LevelOutcome.PASSWORD_IN_USE, controller.setDuressSecret("123456".toCharArray()))

        verify(exactly = 0) { pinComponent.setDuressPin(any()) }
    }

    @Test
    fun setDuressSecret_noLevelLeft_reportsTheLimit() = runTest(dispatcher) {
        every { pinComponent.getDuressLevel() } throws IllegalStateException("too many duress levels")

        assertEquals(LevelOutcome.LIMIT_REACHED, controller.setDuressSecret("123456".toCharArray()))

        verify(exactly = 0) { pinComponent.setDuressPin(any()) }
    }

    @Test
    fun setDuressSecret_storageFails_failed() = runTest(dispatcher) {
        every { pinComponent.getDuressLevel() } returns 1
        every { pinComponent.isUnique(any(), true) } returns true
        every { pinComponent.setDuressPin(any()) } throws IllegalStateException("disk")

        assertEquals(LevelOutcome.FAILED, controller.setDuressSecret("123456".toCharArray()))
    }

    @Test
    fun createHiddenWallet_pinAllowedByThePolicy_storesItAndEntersTheLevel() = runTest(dispatcher) {
        every { hiddenWalletPinPolicy.canUse("123456") } returns true
        every { pinComponent.setHiddenWalletPin("123456") } returns -1

        assertEquals(LevelOutcome.SUCCESS, controller.createHiddenWallet("123456".toCharArray()))

        verify { userManager.setUserLevel(-1) }
    }

    @Test
    fun createHiddenWallet_pinRejectedByThePolicy_isInUseAndStoresNothing() = runTest(dispatcher) {
        every { hiddenWalletPinPolicy.canUse("123456") } returns false

        assertEquals(LevelOutcome.PASSWORD_IN_USE, controller.createHiddenWallet("123456".toCharArray()))

        verify(exactly = 0) { pinComponent.setHiddenWalletPin(any()) }
        verify(exactly = 0) { userManager.setUserLevel(any()) }
    }

    @Test
    fun isDuressAvailable_mainLevel_true() = runTest(dispatcher) {
        every { userManager.getUserLevel() } returns 0

        assertTrue(controller.isDuressAvailable())
    }

    @Test
    fun isDuressAvailable_insideHiddenWallet_false() = runTest(dispatcher) {
        every { userManager.getUserLevel() } returns -1

        assertFalse(controller.isDuressAvailable())
    }
}
