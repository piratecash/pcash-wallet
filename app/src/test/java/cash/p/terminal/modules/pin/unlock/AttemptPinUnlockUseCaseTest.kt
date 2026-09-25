package cash.p.terminal.modules.pin.unlock

import cash.p.terminal.feature.logging.domain.usecase.DeleteLoggingOnDuressUseCase
import cash.p.terminal.feature.logging.domain.usecase.LogLoginAttemptUseCase
import cash.p.terminal.modules.pin.SendZecOnDuressUseCase
import cash.p.terminal.modules.pin.core.ILockoutManager
import cash.p.terminal.modules.pin.core.PinLevels
import cash.p.terminal.wallet.AccountDeletionBlockedException
import io.mockk.Called
import io.mockk.verify
import io.horizontalsystems.core.IPinComponent
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class AttemptPinUnlockUseCaseTest {

    @MockK
    private lateinit var pinComponent: IPinComponent

    @MockK
    private lateinit var lockoutManager: ILockoutManager

    @MockK
    private lateinit var logLoginAttemptUseCase: LogLoginAttemptUseCase

    @MockK
    private lateinit var deleteLoggingOnDuressUseCase: DeleteLoggingOnDuressUseCase

    @MockK
    private lateinit var sendZecOnDuressUseCase: SendZecOnDuressUseCase

    private lateinit var useCase: AttemptPinUnlockUseCase

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxUnitFun = true)

        useCase = AttemptPinUnlockUseCase(
            pinComponent = pinComponent,
            lockoutManager = lockoutManager,
            logLoginAttemptUseCase = logLoginAttemptUseCase,
            deleteLoggingOnDuressUseCase = deleteLoggingOnDuressUseCase,
            sendZecOnDuressUseCase = sendZecOnDuressUseCase,
        )
    }

    @Test
    fun invoke_mainPin_unlocksAndRunsDuressSideEffectsForLevelZero() = runTest {
        val pin = "1234"
        every { pinComponent.getPinLevel(pin) } returns 0
        coEvery { logLoginAttemptUseCase.captureLoginPhoto(0) } returns "/photo.jpg"
        coEvery { pinComponent.unlock(pin, 0) } returns true
        coEvery { logLoginAttemptUseCase.logLoginAttempt(0, "/photo.jpg") } returns Unit

        val result = useCase(pin)

        assertEquals(AttemptPinUnlockUseCase.Result.Unlocked, result)
        coVerify { pinComponent.unlock(pin, 0) }
        coVerify { logLoginAttemptUseCase.logLoginAttempt(userLevel = 0, photoPath = "/photo.jpg") }
        coVerify { lockoutManager.dropFailedAttempts() }
        coVerify { deleteLoggingOnDuressUseCase.deleteLoggingForLowerLevelsIfEnabled(0) }
        coVerify { sendZecOnDuressUseCase.sendIfEnabled(0) }
        coVerify(exactly = 0) { lockoutManager.didFailUnlock() }
    }

    @Test
    fun invoke_duressPin_unlocksAndTriggersDuressSideEffects() = runTest {
        val pin = "5555"
        val duressLevel = 1
        every { pinComponent.getPinLevel(pin) } returns duressLevel
        coEvery { logLoginAttemptUseCase.captureLoginPhoto(duressLevel) } returns null
        coEvery { pinComponent.unlock(pin, duressLevel) } returns true
        coEvery { logLoginAttemptUseCase.logLoginAttempt(duressLevel, null) } returns Unit

        val result = useCase(pin)

        assertEquals(AttemptPinUnlockUseCase.Result.Unlocked, result)
        coVerify { pinComponent.unlock(pin, duressLevel) }
        coVerify { logLoginAttemptUseCase.logLoginAttempt(userLevel = duressLevel, photoPath = null) }
        coVerify { lockoutManager.dropFailedAttempts() }
        coVerify { deleteLoggingOnDuressUseCase.deleteLoggingForLowerLevelsIfEnabled(duressLevel) }
        coVerify { sendZecOnDuressUseCase.sendIfEnabled(duressLevel) }
        coVerify(exactly = 0) { lockoutManager.didFailUnlock() }
    }

    @Test
    fun invoke_wrongPin_returnsInvalidPinAndRecordsFailure() = runTest {
        val pin = "0000"
        every { pinComponent.getPinLevel(pin) } returns null
        coEvery { logLoginAttemptUseCase.captureLoginPhoto(null) } returns "/photo.jpg"
        coEvery { pinComponent.unlock(pin, null) } returns false
        coEvery { logLoginAttemptUseCase.logLoginAttempt(null, "/photo.jpg") } returns Unit

        val result = useCase(pin)

        assertEquals(AttemptPinUnlockUseCase.Result.InvalidPin, result)
        coVerify { logLoginAttemptUseCase.logLoginAttempt(userLevel = null, photoPath = "/photo.jpg") }
        coVerify { lockoutManager.didFailUnlock() }
        coVerify(exactly = 0) { lockoutManager.dropFailedAttempts() }
        coVerify(exactly = 0) { deleteLoggingOnDuressUseCase.deleteLoggingForLowerLevelsIfEnabled(any()) }
        coVerify(exactly = 0) { sendZecOnDuressUseCase.sendIfEnabled(any()) }
    }

    @Test
    fun invoke_secureResetPin_resolvesToLevelZeroAndUnlocks() = runTest {
        val pin = "9999"
        every { pinComponent.getPinLevel(pin) } returns PinLevels.SECURE_RESET
        coEvery { logLoginAttemptUseCase.captureLoginPhoto(0) } returns null
        coEvery { pinComponent.unlock(pin, PinLevels.SECURE_RESET) } returns true
        coEvery { logLoginAttemptUseCase.logLoginAttempt(0, null) } returns Unit

        val result = useCase(pin)

        assertEquals(AttemptPinUnlockUseCase.Result.Unlocked, result)
        coVerify { pinComponent.unlock(pin, PinLevels.SECURE_RESET) }
        coVerify { logLoginAttemptUseCase.logLoginAttempt(userLevel = 0, photoPath = null) }
        coVerify { lockoutManager.dropFailedAttempts() }
        coVerify { deleteLoggingOnDuressUseCase.deleteLoggingForLowerLevelsIfEnabled(0) }
        coVerify { sendZecOnDuressUseCase.sendIfEnabled(0) }
    }

    @Test
    fun invoke_blockedSecureReset_returnsDistinctResultWithoutFailureOrDuressEffects() = runTest {
        every { pinComponent.getPinLevel("9999") } returns PinLevels.SECURE_RESET
        coEvery { logLoginAttemptUseCase.captureLoginPhoto(0) } returns "/private/test-login-photo.jpg"
        coEvery { pinComponent.unlock("9999", PinLevels.SECURE_RESET) } throws AccountDeletionBlockedException()

        assertEquals(AttemptPinUnlockUseCase.Result.ResetBlocked, useCase("9999"))

        verify { listOf(lockoutManager, deleteLoggingOnDuressUseCase, sendZecOnDuressUseCase) wasNot Called }
        coVerify(exactly = 1) { logLoginAttemptUseCase.discardCapturedPhoto("/private/test-login-photo.jpg") }
        coVerify(exactly = 0) { logLoginAttemptUseCase.logLoginAttempt(any(), any()) }
    }

    @Test
    fun invoke_logLoggingPin_returnsInvalidPinAndRecordsFailure() = runTest {
        val pin = "7777"
        val logLoggingLevel = PinLevels.LOG_LOGGING_BASE
        every { pinComponent.getPinLevel(pin) } returns logLoggingLevel
        coEvery { logLoginAttemptUseCase.captureLoginPhoto(null) } returns null
        coEvery { pinComponent.unlock(pin, logLoggingLevel) } returns false
        coEvery { logLoginAttemptUseCase.logLoginAttempt(null, null) } returns Unit

        val result = useCase(pin)

        assertEquals(AttemptPinUnlockUseCase.Result.InvalidPin, result)
        coVerify { logLoginAttemptUseCase.captureLoginPhoto(null) }
        coVerify { logLoginAttemptUseCase.logLoginAttempt(userLevel = null, photoPath = null) }
        coVerify { lockoutManager.didFailUnlock() }
        coVerify(exactly = 0) { lockoutManager.dropFailedAttempts() }
        coVerify(exactly = 0) { deleteLoggingOnDuressUseCase.deleteLoggingForLowerLevelsIfEnabled(any()) }
        coVerify(exactly = 0) { sendZecOnDuressUseCase.sendIfEnabled(any()) }
    }

    @Test
    fun invoke_deleteContactsPin_returnsInvalidPinAndRecordsFailure() = runTest {
        val pin = "8888"
        val deleteContactsLevel = PinLevels.DELETE_CONTACTS
        every { pinComponent.getPinLevel(pin) } returns deleteContactsLevel
        coEvery { logLoginAttemptUseCase.captureLoginPhoto(null) } returns null
        coEvery { pinComponent.unlock(pin, deleteContactsLevel) } returns false
        coEvery { logLoginAttemptUseCase.logLoginAttempt(null, null) } returns Unit

        val result = useCase(pin)

        assertEquals(AttemptPinUnlockUseCase.Result.InvalidPin, result)
        coVerify { logLoginAttemptUseCase.logLoginAttempt(userLevel = null, photoPath = null) }
        coVerify { lockoutManager.didFailUnlock() }
        coVerify(exactly = 0) { lockoutManager.dropFailedAttempts() }
        coVerify(exactly = 0) { deleteLoggingOnDuressUseCase.deleteLoggingForLowerLevelsIfEnabled(any()) }
        coVerify(exactly = 0) { sendZecOnDuressUseCase.sendIfEnabled(any()) }
    }

    @Test
    fun invoke_pinComponentReturnsFalse_doesNotRunDuressSideEffectsEvenWithKnownLevel() = runTest {
        val pin = "1234"
        every { pinComponent.getPinLevel(pin) } returns 0
        coEvery { logLoginAttemptUseCase.captureLoginPhoto(0) } returns null
        coEvery { pinComponent.unlock(pin, 0) } returns false
        coEvery { logLoginAttemptUseCase.logLoginAttempt(null, null) } returns Unit

        val result = useCase(pin)

        assertEquals(AttemptPinUnlockUseCase.Result.InvalidPin, result)
        coVerify { logLoginAttemptUseCase.logLoginAttempt(userLevel = null, photoPath = null) }
        coVerify { lockoutManager.didFailUnlock() }
        coVerify(exactly = 0) { lockoutManager.dropFailedAttempts() }
        coVerify(exactly = 0) { deleteLoggingOnDuressUseCase.deleteLoggingForLowerLevelsIfEnabled(any()) }
        coVerify(exactly = 0) { sendZecOnDuressUseCase.sendIfEnabled(any()) }
    }

    @Test
    fun invoke_capturePhotoBeforeUnlock_logsResolvedLevelOnSuccess() = runTest {
        val pin = "1111"
        val duressLevel = 2
        every { pinComponent.getPinLevel(pin) } returns duressLevel
        coEvery { logLoginAttemptUseCase.captureLoginPhoto(duressLevel) } returns "/duress.jpg"
        coEvery { pinComponent.unlock(pin, duressLevel) } returns true
        coEvery { logLoginAttemptUseCase.logLoginAttempt(duressLevel, "/duress.jpg") } returns Unit

        val result = useCase(pin)

        assertEquals(AttemptPinUnlockUseCase.Result.Unlocked, result)
        coVerify { logLoginAttemptUseCase.captureLoginPhoto(duressLevel) }
        coVerify { logLoginAttemptUseCase.logLoginAttempt(userLevel = duressLevel, photoPath = "/duress.jpg") }
    }
}
