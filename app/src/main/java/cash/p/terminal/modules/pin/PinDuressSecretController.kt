package cash.p.terminal.modules.pin

import cash.p.terminal.core.tryOrNull
import cash.p.terminal.modules.pin.hiddenwallet.HiddenWalletPinPolicy
import cash.p.terminal.wallet.managers.UserManager
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.IPinComponent
import kotlinx.coroutines.withContext

class PinDuressSecretController(
    private val pinComponent: IPinComponent,
    private val userManager: UserManager,
    private val hiddenWalletPinPolicy: HiddenWalletPinPolicy,
    private val dispatcherProvider: DispatcherProvider,
) : DuressSecretController {

    override suspend fun setDuressSecret(secret: CharArray): LevelOutcome = withContext(dispatcherProvider.io) {
        useAndWipe(secret) { pin ->
            when {
                tryOrNull { pinComponent.getDuressLevel() } == null -> LevelOutcome.LIMIT_REACHED
                !pinComponent.isUnique(pin, forDuress = true) -> LevelOutcome.PASSWORD_IN_USE
                else -> saved { pinComponent.setDuressPin(pin) }
            }
        }
    }

    override suspend fun disableDuress() = withContext(dispatcherProvider.io) {
        pinComponent.disableDuressPin()
    }

    override suspend fun createHiddenWallet(secret: CharArray): LevelOutcome = withContext(dispatcherProvider.io) {
        useAndWipe(secret) { pin ->
            if (!hiddenWalletPinPolicy.canUse(pin)) return@useAndWipe LevelOutcome.PASSWORD_IN_USE
            saved { userManager.setUserLevel(pinComponent.setHiddenWalletPin(pin)) }
        }
    }

    override suspend fun isDuressSet(): Boolean = withContext(dispatcherProvider.io) {
        pinComponent.isDuressPinSet()
    }

    override suspend fun isDuressAvailable(): Boolean = userManager.getUserLevel() >= 0

    private fun saved(save: () -> Unit): LevelOutcome =
        if (tryOrNull(save) != null) LevelOutcome.SUCCESS else LevelOutcome.FAILED

    private fun <T> useAndWipe(secret: CharArray, block: (String) -> T): T = try {
        block(String(secret))
    } finally {
        secret.fill('\u0000')
    }
}
