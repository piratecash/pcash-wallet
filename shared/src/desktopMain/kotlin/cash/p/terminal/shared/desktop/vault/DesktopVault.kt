package cash.p.terminal.shared.desktop.vault

import cash.p.terminal.modules.pin.core.ILockoutManager
import cash.p.terminal.modules.pin.core.LockoutState
import cash.p.terminal.vault.ChangePasswordResult
import cash.p.terminal.vault.CreateResult
import cash.p.terminal.vault.PasswordVault
import cash.p.terminal.vault.UnlockResult
import cash.p.terminal.vault.VaultSession
import co.touchlab.kermit.Logger
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date

/**
 * The desktop password gate: owns the slot file, the unlocked session and the lockout counter.
 * Every operation takes ownership of the passwords handed to it and wipes them.
 */
class DesktopVault(
    private val files: DesktopVaultFiles,
    private val passwordVault: PasswordVault,
    private val lockoutManager: ILockoutManager,
    private val preferencesReset: DesktopPreferencesReset,
    private val dispatcherProvider: DispatcherProvider,
    private val driverFactory: SqlCipherDriverFactory,
) {
    private val logger = Logger.withTag("DesktopVault")
    private val mutex = Mutex()

    private val _state = MutableStateFlow<DesktopVaultState>(
        if (passwordVault.exists()) DesktopVaultState.Locked else DesktopVaultState.NoVault
    )
    val state: StateFlow<DesktopVaultState> = _state.asStateFlow()

    suspend fun create(password: CharArray): CreateOutcome =
        operation("create", CreateOutcome.FAILED, password) { createInternal(password) }

    suspend fun unlock(password: CharArray): UnlockOutcome =
        operation("unlock", UnlockOutcome.Failed, password) { unlockInternal(password) }

    /** The persisted lockout, so a restarted unlock screen shows it before any attempt. */
    suspend fun lockedOutUntil(): Date? = withContext(dispatcherProvider.io) {
        (lockoutManager.currentState as? LockoutState.Locked)?.until
    }

    suspend fun changePassword(old: CharArray, new: CharArray): ChangePasswordOutcome =
        operation("changePassword", ChangePasswordOutcome.FAILED, old, new) {
            when (passwordVault.changePassword(old, new)) {
                ChangePasswordResult.Success -> ChangePasswordOutcome.SUCCESS
                ChangePasswordResult.WrongPassword -> ChangePasswordOutcome.WRONG_PASSWORD
                ChangePasswordResult.Corrupted -> ChangePasswordOutcome.CORRUPTED
            }
        }

    /** Covers the application with the unlock screen first, then takes the data down. */
    suspend fun lock() = mutex.withLock {
        val session = (_state.value as? DesktopVaultState.Unlocked)?.session ?: return@withLock
        _state.value = DesktopVaultState.Locked
        // Once the screen says locked, the keys have to go down even if the caller is cancelled.
        withContext(NonCancellable + dispatcherProvider.io) { session.close() }
    }

    suspend fun wipe(): WipeOutcome = operation("wipe", WipeOutcome.FAILED) { wipeInternal() }

    private fun createInternal(password: CharArray): CreateOutcome {
        // A second press after a successful first one must not touch the storage it just created.
        if (_state.value !is DesktopVaultState.NoVault) return CreateOutcome.SUCCESS
        if (passwordVault.exists()) {
            _state.value = DesktopVaultState.Locked
            return CreateOutcome.SUCCESS
        }
        // The state stays NoVault while nothing has been published, so a retry starts over.
        if (!deleteResidualData()) return CreateOutcome.FAILED
        return when (val result = passwordVault.create(password)) {
            is CreateResult.Created -> startSession(result.session, CreateOutcome.SUCCESS, CreateOutcome.FAILED)
            CreateResult.AlreadyExists -> {
                _state.value = DesktopVaultState.Locked
                CreateOutcome.SUCCESS
            }
            CreateResult.Failed -> CreateOutcome.FAILED
        }
    }

    private fun unlockInternal(password: CharArray): UnlockOutcome {
        // A second press after a successful first one must not open a second session over the open one.
        if (_state.value is DesktopVaultState.Unlocked) return UnlockOutcome.Success
        // Covers a request queued on the mutex before the lockout began.
        (lockoutManager.currentState as? LockoutState.Locked)?.let { return UnlockOutcome.LockedOut(it.until) }
        return when (val result = passwordVault.unlock(password)) {
            is UnlockResult.Success -> unlockSucceeded(result.session)
            UnlockResult.WrongPassword -> {
                lockoutManager.didFailUnlock()
                when (val lockout = lockoutManager.currentState) {
                    is LockoutState.Locked -> UnlockOutcome.LockedOut(lockout.until)
                    is LockoutState.Unlocked -> UnlockOutcome.WrongPassword(lockout.attemptsLeft)
                }
            }
            UnlockResult.Corrupted -> UnlockOutcome.Corrupted
        }
    }

    /** The counter reset writes to disk, so its failure must not leave the fresh session open. */
    private fun unlockSucceeded(vaultSession: VaultSession): UnlockOutcome {
        try {
            lockoutManager.dropFailedAttempts()
        } catch (e: Throwable) {
            vaultSession.close()
            throw e
        }
        return startSession(vaultSession, UnlockOutcome.Success, UnlockOutcome.StorageUnavailable)
    }

    private fun <T> startSession(vaultSession: VaultSession, success: T, failure: T): T {
        val session = openSession(vaultSession)
        _state.value = session?.let(DesktopVaultState::Unlocked) ?: DesktopVaultState.Locked
        return if (session == null) failure else success
    }

    /** Missing SQLCipher natives surface as a LinkageError; that is an open failure like any other. */
    private fun openSession(vaultSession: VaultSession): DesktopStorageSession? {
        val cause = try {
            return DesktopStorageSession.open(vaultSession, files, driverFactory)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e
        } catch (e: LinkageError) {
            e
        }
        logger.w(cause) { "Opening the storage session failed" }
        return null
    }

    private suspend fun wipeInternal(): WipeOutcome {
        (_state.value as? DesktopVaultState.Unlocked)?.session?.close()
        _state.value = DesktopVaultState.Locked
        // Deleting the slots is the crypto-erase: without them neither the database nor kit keys open.
        if (!delete(files.slotFiles())) return WipeOutcome.FAILED
        // Past this point the keys are gone, so a leftover file is data nobody can read.
        delete(files.databaseFiles())
        files.kitsDirectory.deleteRecursively()
        preferencesReset.resetToDefaults()
        _state.value = DesktopVaultState.NoVault
        return WipeOutcome.SUCCESS
    }

    private fun deleteResidualData(): Boolean =
        delete(files.databaseFiles() + files.slotFiles()) && files.kitsDirectory.deleteRecursively()

    private fun delete(targets: List<File>): Boolean = targets.all { !it.exists() || it.delete() }

    private suspend fun <T> operation(
        name: String,
        failure: T,
        vararg passwords: CharArray,
        block: suspend () -> T,
    ): T = try {
        mutex.withLock {
            try {
                withContext(dispatcherProvider.io) { block() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.w(e) { "Vault operation $name failed" }
                failure
            }
        }
    } finally {
        // Outside the lock on purpose: a request cancelled while queued owns its password too.
        passwords.forEach { it.fill('\u0000') }
    }
}
