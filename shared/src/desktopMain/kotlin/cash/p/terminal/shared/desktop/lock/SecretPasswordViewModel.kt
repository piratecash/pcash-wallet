package cash.p.terminal.shared.desktop.lock

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.modules.pin.DuressSecretController
import cash.p.terminal.modules.pin.LevelOutcome
import cash.p.terminal.wallet.PassphraseValidator
import cash.p.terminal.wallet.PasswordRequirement
import cash.p.terminal.wallet.managers.UserManager
import co.touchlab.kermit.Logger
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SecretPasswordUiState(
    val password: String = "",
    val confirmation: String = "",
    val unmetRequirements: List<PasswordRequirement> = PasswordRequirement.entries,
    val invalidCharacters: Boolean = false,
    val inProgress: Boolean = false,
    val outcome: LevelOutcome? = null,
) {
    val mismatch: Boolean get() = confirmation.isNotEmpty() && confirmation != password

    val finished: Boolean get() = outcome == LevelOutcome.SUCCESS

    val canSubmit: Boolean
        get() = unmetRequirements.isEmpty() &&
            !invalidCharacters &&
            confirmation == password &&
            !inProgress &&
            !finished
}

/** The form of a password for a level beyond the main one; [save] decides what the password is for. */
abstract class SecretPasswordViewModel(
    private val passphraseValidator: PassphraseValidator,
) : ViewModel() {

    var uiState by mutableStateOf(SecretPasswordUiState())
        private set

    fun onPasswordChange(value: String) {
        uiState = uiState.copy(
            password = value,
            unmetRequirements = passphraseValidator.unmetRequirements(value),
            invalidCharacters = !passphraseValidator.containsValidCharacters(value),
            outcome = null,
        )
    }

    fun onConfirmationChange(value: String) {
        uiState = uiState.copy(confirmation = value, outcome = null)
    }

    fun onSubmitClick() {
        if (!uiState.canSubmit) return
        uiState = uiState.copy(inProgress = true, outcome = null)
        viewModelScope.launch {
            // Back mid-save must not leave a half-made level; the callee wipes the array, whatever the outcome.
            val outcome = withContext(NonCancellable) { save(uiState.password.toCharArray()) }
            uiState = uiState.copy(inProgress = false, outcome = outcome)
        }
    }

    protected abstract suspend fun save(secret: CharArray): LevelOutcome
}

/** Creates the duress level, or changes its password when [accountIds] is empty. */
class DuressPasswordViewModel(
    private val accountIds: List<String>,
    private val controller: DuressSecretController,
    private val userManager: UserManager,
    private val dispatcherProvider: DispatcherProvider,
    passphraseValidator: PassphraseValidator,
) : SecretPasswordViewModel(passphraseValidator) {

    private val logger = Logger.withTag("DuressPasswordViewModel")

    override suspend fun save(secret: CharArray): LevelOutcome {
        val outcome = controller.setDuressSecret(secret)
        if (outcome != LevelOutcome.SUCCESS || accountIds.isEmpty()) return outcome
        return try {
            withContext(dispatcherProvider.io) { userManager.allowAccountsForDuress(accountIds) }
            outcome
        } catch (e: Exception) {
            logger.w(e) { "Moving the wallets to the duress level failed" }
            // A duress level without the chosen wallets would open an empty app.
            controller.disableDuress()
            LevelOutcome.FAILED
        }
    }
}

class HiddenWalletPasswordViewModel(
    private val controller: DuressSecretController,
    passphraseValidator: PassphraseValidator,
) : SecretPasswordViewModel(passphraseValidator) {

    override suspend fun save(secret: CharArray): LevelOutcome = controller.createHiddenWallet(secret)
}
