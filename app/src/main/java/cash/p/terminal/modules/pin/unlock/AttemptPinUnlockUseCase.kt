package cash.p.terminal.modules.pin.unlock

import cash.p.terminal.feature.logging.domain.usecase.DeleteLoggingOnDuressUseCase
import cash.p.terminal.feature.logging.domain.usecase.LogLoginAttemptUseCase
import cash.p.terminal.modules.pin.SendZecOnDuressUseCase
import cash.p.terminal.modules.pin.core.ILockoutManager
import cash.p.terminal.modules.pin.core.PinLevels
import cash.p.terminal.wallet.AccountDeletionBlockedException
import io.horizontalsystems.core.IPinComponent
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class AttemptPinUnlockUseCase(
    private val pinComponent: IPinComponent,
    private val lockoutManager: ILockoutManager,
    private val logLoginAttemptUseCase: LogLoginAttemptUseCase,
    private val deleteLoggingOnDuressUseCase: DeleteLoggingOnDuressUseCase,
    private val sendZecOnDuressUseCase: SendZecOnDuressUseCase,
) {
    enum class Result { Unlocked, InvalidPin, ResetBlocked }

    suspend operator fun invoke(pin: String): Result {
        val detectedPinLevel = pinComponent.getPinLevel(pin)
        val userLevel = PinLevels.resolvedUserLevelAfterUnlock(detectedPinLevel)

        val photoPath = logLoginAttemptUseCase.captureLoginPhoto(userLevel)
        val unlocked = try {
            pinComponent.unlock(pin, detectedPinLevel)
        } catch (_: AccountDeletionBlockedException) {
            withContext(NonCancellable) { logLoginAttemptUseCase.discardCapturedPhoto(photoPath) }
            return Result.ResetBlocked
        }

        logLoginAttemptUseCase.logLoginAttempt(
            userLevel = userLevel.takeIf { unlocked },
            photoPath = photoPath
        )

        if (unlocked && userLevel != null) {
            lockoutManager.dropFailedAttempts()
            deleteLoggingOnDuressUseCase.deleteLoggingForLowerLevelsIfEnabled(userLevel)
            sendZecOnDuressUseCase.sendIfEnabled(userLevel)
            return Result.Unlocked
        }
        lockoutManager.didFailUnlock()
        return Result.InvalidPin
    }
}
