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
 * The desktop password gate: owns the slot file, the storage session and the lockout counter.
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

    @Volatile
    private var session: DesktopStorageSession? = null

    // Written and read under the mutex only.
    private var wipeStarted = false

    private val _state = MutableStateFlow<DesktopVaultState>(
        if (passwordVault.exists()) DesktopVaultState.Locked else DesktopVaultState.NoVault
    )
    val state: StateFlow<DesktopVaultState> = _state.asStateFlow()

    suspend fun create(password: CharArray): CreateOutcome =
        operation("create", CreateOutcome.FAILED, password) { createInternal(password) }

    suspend fun unlock(password: CharArray): UnlockOutcome =
        operation("unlock", UnlockOutcome.Failed, password) { unlockInternal(password) }

    /** Re-asks the password inside the app; failures count into the same lockout as unlock. */
    suspend fun verifyPassword(password: CharArray): UnlockOutcome =
        operation("verifyPassword", UnlockOutcome.Failed, password) {
            when (val check = checkPassword(password)) {
                is PasswordCheck.Correct -> {
                    check.vaultSession.close()
                    UnlockOutcome.Success
                }
                is PasswordCheck.Wrong -> UnlockOutcome.WrongPassword(check.attemptsLeft)
                is PasswordCheck.LockedOut -> UnlockOutcome.LockedOut(check.until)
                PasswordCheck.Corrupted -> UnlockOutcome.Corrupted
            }
        }

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

    /**
     * The storage of the current process: null before the first unlock or create and after a wipe or quit.
     * The lock does not close it, so screens and singletons that outlive a lock keep working.
     */
    fun openSession(): DesktopStorageSession? = session

    /** Covers the application with the unlock screen; the storage stays open until wipe or quit. */
    suspend fun lock() = mutex.withLock {
        if (_state.value is DesktopVaultState.Unlocked) _state.value = DesktopVaultState.Locked
    }

    /** App quit: covers the application first, then takes the data down. */
    suspend fun close() = mutex.withLock {
        if (_state.value is DesktopVaultState.Unlocked) _state.value = DesktopVaultState.Locked
        // Once the screen says locked, the keys have to go down even if the caller is cancelled.
        withContext(NonCancellable + dispatcherProvider.io) { closeSession() }
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
        val vaultSession = when (val check = checkPassword(password)) {
            is PasswordCheck.Correct -> check.vaultSession
            is PasswordCheck.Wrong -> return UnlockOutcome.WrongPassword(check.attemptsLeft)
            is PasswordCheck.LockedOut -> return UnlockOutcome.LockedOut(check.until)
            PasswordCheck.Corrupted -> return UnlockOutcome.Corrupted
        }
        val openSession = session
            ?: return startSession(vaultSession, UnlockOutcome.Success, UnlockOutcome.StorageUnavailable)
        // The storage stayed open under the lock: the password only proves who came back.
        vaultSession.close()
        _state.value = DesktopVaultState.Unlocked(openSession)
        return UnlockOutcome.Success
    }

    /** The slot check under the shared lockout counter; a correct password hands over its vault session. */
    private fun checkPassword(password: CharArray): PasswordCheck {
        // Covers a request queued on the mutex before the lockout began.
        (lockoutManager.currentState as? LockoutState.Locked)?.let { return PasswordCheck.LockedOut(it.until) }
        return when (val result = passwordVault.unlock(password)) {
            is UnlockResult.Success -> passwordAccepted(result.session)
            UnlockResult.WrongPassword -> {
                lockoutManager.didFailUnlock()
                when (val lockout = lockoutManager.currentState) {
                    is LockoutState.Locked -> PasswordCheck.LockedOut(lockout.until)
                    is LockoutState.Unlocked -> PasswordCheck.Wrong(lockout.attemptsLeft)
                }
            }
            UnlockResult.Corrupted -> PasswordCheck.Corrupted
        }
    }

    /** The counter reset writes to disk, so its failure must not leave the fresh vault session open. */
    private fun passwordAccepted(vaultSession: VaultSession): PasswordCheck {
        // A slot file swapped under the open storage must not unlock it with its own password.
        if (session?.isOpenedBy(vaultSession) == false) {
            vaultSession.close()
            logger.w { "The slot file does not belong to the open storage" }
            return PasswordCheck.Corrupted
        }
        try {
            lockoutManager.dropFailedAttempts()
        } catch (e: Throwable) {
            vaultSession.close()
            throw e
        }
        return PasswordCheck.Correct(vaultSession)
    }

    private fun <T> startSession(vaultSession: VaultSession, success: T, failure: T): T {
        // Whatever the wipe outcome, only a new process may open the storage again.
        if (wipeStarted) {
            vaultSession.close()
            logger.w { "Storage session refused: this process has started a wipe" }
            return failure
        }
        val opened = openStorage(vaultSession)
        session = opened
        _state.value = opened?.let(DesktopVaultState::Unlocked) ?: DesktopVaultState.Locked
        return if (opened == null) failure else success
    }

    /** Missing SQLCipher natives surface as a LinkageError; that is an open failure like any other. */
    private fun openStorage(vaultSession: VaultSession): DesktopStorageSession? {
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

    /** Unpublished before it is closed, so no reader gets a session that is going down. */
    private fun closeSession() {
        val closing = session ?: return
        session = null
        closing.close()
    }

    private suspend fun wipeInternal(): WipeOutcome {
        wipeStarted = true
        closeSession()
        _state.value = DesktopVaultState.Locked
        // Before the erase: a restart without slots must not find the old preferences.
        preferencesReset.resetStored()
        // Deleting the slots is the crypto-erase: without them neither the database nor kit keys open.
        if (!delete(files.slotFiles())) return WipeOutcome.FAILED
        // Past this point the keys are gone, so a leftover file is data nobody can read.
        delete(files.databaseFiles())
        if (!files.kitsDirectory.deleteRecursively()) {
            logger.w { "Kit files outlived the wipe in ${files.kitsDirectory}; the next create() deletes them" }
        }
        preferencesReset.applySystemLanguage()
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

private sealed interface PasswordCheck {
    class Correct(val vaultSession: VaultSession) : PasswordCheck
    class Wrong(val attemptsLeft: Int?) : PasswordCheck
    class LockedOut(val until: Date) : PasswordCheck
    data object Corrupted : PasswordCheck
}
