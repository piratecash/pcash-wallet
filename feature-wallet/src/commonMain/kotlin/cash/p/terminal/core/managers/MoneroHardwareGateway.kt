package cash.p.terminal.core.managers

import cash.p.terminal.wallet.Account
import com.piratecash.monero.signer.ExternalSignerRegistration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job

/** Runs a Monero kit call with the account's hardware wallet registered as the kit's external signer. */
interface MoneroHardwareGateway {
    /** [block] receives the device's wallet public key. */
    suspend fun <T> execute(account: Account, block: (String) -> T): T
}

@OptIn(InternalCoroutinesApi::class)
suspend fun <T> withExternalSignerRegistration(
    registration: ExternalSignerRegistration,
    block: suspend () -> T,
): T {
    val cancellationHandle = currentCoroutineContext().job.invokeOnCompletion(
        onCancelling = true,
        invokeImmediately = true,
    ) { cause ->
        if (cause is CancellationException) {
            try {
                registration.signalCancellation()
            } catch (error: Throwable) {
                cause.addSuppressed(error)
            }
        }
    }
    return try {
        val outcome = try {
            RegistrationOutcome.Success(block())
        } catch (error: Throwable) {
            RegistrationOutcome.Failure(error)
        }
        val cleanupFailure = try {
            registration.release()
            null
        } catch (error: Throwable) {
            error
        }
        when (outcome) {
            is RegistrationOutcome.Success -> {
                if (cleanupFailure != null) {
                    throw cleanupFailure
                }
                outcome.value
            }

            is RegistrationOutcome.Failure -> {
                cleanupFailure?.let(outcome.error::addSuppressed)
                throw outcome.error
            }
        }
    } finally {
        cancellationHandle.dispose()
    }
}

private sealed interface RegistrationOutcome<out T> {
    data class Success<T>(val value: T) : RegistrationOutcome<T>
    data class Failure(val error: Throwable) : RegistrationOutcome<Nothing>
}
