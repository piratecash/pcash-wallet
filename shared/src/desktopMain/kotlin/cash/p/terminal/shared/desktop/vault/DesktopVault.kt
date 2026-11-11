package cash.p.terminal.shared.desktop.vault

import cash.p.terminal.core.StorageLockedException
import cash.p.terminal.modules.pin.LevelOutcome
import cash.p.terminal.modules.pin.core.ILockoutManager
import cash.p.terminal.modules.pin.core.LockoutState
import cash.p.terminal.vault.CreateResult
import cash.p.terminal.vault.PasswordVault
import cash.p.terminal.vault.UnlockResult
import cash.p.terminal.vault.VaultSession
import co.touchlab.kermit.Logger
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.util.Date
import kotlin.time.Duration.Companion.seconds

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
    driverFactory: SqlCipherDriverFactory,
    checkpoints: StorageCheckpoints,
    private val walletLayer: Lazy<DesktopWalletLayer>,
) {
    private val logger = Logger.withTag("DesktopVault")
    private val releaseScope = CoroutineScope(dispatcherProvider.io + SupervisorJob())
    private val mutex = Mutex()
    private val lock = object : VaultLock {
        override fun <T> exclusive(block: () -> T): T = runBlocking { mutex.withLock { block() } }
    }
    private val environment = StorageEnvironment(files, passwordVault, driverFactory, checkpoints, lock)

    private val session = MutableStateFlow<DesktopStorageSession?>(null)
    internal val sessions: StateFlow<DesktopStorageSession?> = session.asStateFlow()

    // Written and read under the mutex only.
    private var wipeStarted = false

    @Volatile
    internal var levelRelease: Job? = null
        private set

    private val _state = MutableStateFlow<DesktopVaultState>(
        if (passwordVault.exists()) DesktopVaultState.Locked else DesktopVaultState.NoVault
    )
    val state: StateFlow<DesktopVaultState> = _state.asStateFlow()

    suspend fun create(password: CharArray): CreateOutcome =
        operation("create", CreateOutcome.FAILED, password) { createInternal(password) }

    suspend fun unlock(password: CharArray): UnlockOutcome {
        var admission: Admission = Admission.Done(UnlockOutcome.Failed)
        val outcome: UnlockOutcome
        try {
            operation("unlock", Unit, password) { admission = admit(password) }
        } finally {
            // A password already accepted owns a key and maybe a half-made level change: it always finishes.
            outcome = withContext(NonCancellable + dispatcherProvider.io) { complete(admission) }
        }
        return outcome
    }

    /** Re-asks the password inside the app, of this level only; failures count into the same lockout as unlock. */
    suspend fun verifyPassword(password: CharArray): UnlockOutcome =
        operation("verifyPassword", UnlockOutcome.Failed, password) {
            when (val check = checkPassword(password, ::opensCurrentLevel)) {
                is PasswordCheck.Correct -> {
                    check.vaultSession.close()
                    UnlockOutcome.Success
                }
                is PasswordCheck.Rejected -> check.outcome()
            }
        }

    /** The persisted lockout, so a restarted unlock screen shows it before any attempt. */
    suspend fun lockedOutUntil(): Date? = withContext(dispatcherProvider.io) {
        (lockoutManager.currentState as? LockoutState.Locked)?.until
    }

    suspend fun changePassword(old: CharArray, new: CharArray): ChangePasswordOutcome =
        operation("changePassword", ChangePasswordOutcome.FAILED, old, new) { resealEnvelope(old, new) }

    /**
     * The storage of the current process: null before the first unlock or create and after a wipe or quit.
     * The lock does not close it, so screens and singletons that outlive a lock keep working.
     */
    fun openSession(): DesktopStorageSession? = session.value

    /** For changes to the open storage from outside a vault operation; blocks the calling thread. */
    internal fun <T> exclusive(block: () -> T): T = lock.exclusive(block)

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

    suspend fun setDuressPassword(password: CharArray): LevelOutcome =
        operation("setDuressPassword", LevelOutcome.FAILED, password) { requireLevels().setDuressPassword(password) }

    suspend fun createHiddenWallet(password: CharArray): LevelOutcome =
        operation("createHiddenWallet", LevelOutcome.FAILED, password) { requireLevels().createHidden(password) }

    suspend fun resolveHiddenPassword(password: CharArray): HiddenPasswordResolution =
        operation("resolveHiddenPassword", HiddenPasswordResolution.FAILED, password) {
            requireLevels().resolveHiddenPassword(password)
        }

    suspend fun duressAvailability(): DuressAvailability =
        operation("duressAvailability", DuressAvailability.UNAVAILABLE) { requireLevels().duressAvailability }

    private fun requireLevels(): LevelProvisioner = (session.value ?: throw StorageLockedException()).levels

    private fun resealEnvelope(old: CharArray, new: CharArray): ChangePasswordOutcome =
        when (val check = checkPassword(old, ::opensCurrentLevel)) {
            is PasswordCheck.Correct -> check.vaultSession.use { reseal(it, new) }
            is PasswordCheck.Wrong -> ChangePasswordOutcome.WRONG_PASSWORD
            is PasswordCheck.LockedOut -> ChangePasswordOutcome.LOCKED_OUT
            PasswordCheck.Corrupted, PasswordCheck.LegacyFormat -> ChangePasswordOutcome.CORRUPTED
        }

    private fun reseal(current: VaultSession, new: CharArray): ChangePasswordOutcome {
        if (opensAnotherEnvelope(new, current)) return ChangePasswordOutcome.PASSWORD_IN_USE
        checkNotNull(current.payload()).use { passwordVault.sealEnvelope(current.envelopeIndex, new, it) }
        return ChangePasswordOutcome.SUCCESS
    }

    /** As Android's `isUnique`: a level may keep its own password, not take another's. */
    private fun opensAnotherEnvelope(password: CharArray, current: VaultSession): Boolean =
        when (val result = passwordVault.unlock(password)) {
            is UnlockResult.Success -> result.session.use { it.envelopeIndex != current.envelopeIndex }
            UnlockResult.WrongPassword -> false
            UnlockResult.Corrupted, UnlockResult.LegacyFormat -> throw IOException("Vault file is unusable")
        }

    /** With no storage open there is no level to compare with: any password of the vault will do. */
    private fun opensCurrentLevel(candidate: VaultSession): Boolean =
        session.value?.let { it.levelOpenedBy(candidate) == it.rootLevel } ?: true

    private fun createInternal(password: CharArray): CreateOutcome {
        // A second press after a successful first one must not touch the storage it just created.
        if (_state.value !is DesktopVaultState.NoVault) return CreateOutcome.SUCCESS
        if (passwordVault.exists()) {
            _state.value = DesktopVaultState.Locked
            return CreateOutcome.SUCCESS
        }
        // Whatever the wipe outcome, only a new process may open the storage again.
        if (wipeStarted) return CreateOutcome.FAILED
        // The state stays NoVault while nothing has been published, so a retry starts over.
        if (!deleteResidualData()) return CreateOutcome.FAILED
        var opened: DesktopStorageSession? = null
        val result = passwordVault.create(password) { created -> opened = createStorage(created) }
        val storage = opened
        if (result is CreateResult.Created && storage != null) {
            publish(storage)
            return CreateOutcome.SUCCESS
        }
        storage?.close()
        if (result !is CreateResult.AlreadyExists) return CreateOutcome.FAILED
        _state.value = DesktopVaultState.Locked
        return CreateOutcome.SUCCESS
    }

    /** Missing SQLCipher natives surface as a LinkageError; that is a storage failure like any other. */
    private fun createStorage(created: CreateResult.Created): DesktopStorageSession = try {
        DesktopStorageSession.create(created, environment)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw IOException("Creating the storage failed", e)
    } catch (e: LinkageError) {
        throw IOException("Creating the storage failed", e)
    }

    private fun publish(storage: DesktopStorageSession) {
        session.value = storage
        _state.value = DesktopVaultState.Unlocked(storage)
    }

    /** Same work for every password up to here; what the accepted one means decides what follows. */
    private fun admit(password: CharArray): Admission {
        // A second press after a successful first one must not open a second session over the open one.
        if (_state.value is DesktopVaultState.Unlocked) return Admission.Done(UnlockOutcome.Success)
        val vaultSession = when (val check = checkPassword(password)) {
            is PasswordCheck.Correct -> check.vaultSession
            is PasswordCheck.Rejected -> return Admission.Done(check.outcome())
        }
        val open = session.value ?: return Admission.Done(
            startSession(vaultSession, UnlockOutcome.Success, UnlockOutcome.StorageUnavailable)
        )
        val level = open.levelOpenedBy(vaultSession)
        return when {
            level == open.rootLevel -> {
                // The storage stayed open under the lock: the password only proves who came back.
                vaultSession.close()
                _state.value = DesktopVaultState.Unlocked(open)
                Admission.Done(UnlockOutcome.Success)
            }
            level != null && level > open.rootLevel -> beginNarrowing(open, level, vaultSession)
            else -> Admission.Switching(open, vaultSession)
        }
    }

    /** The target is known, so a narrowing that cannot begin still reaches it by a cold start. */
    private fun beginNarrowing(open: DesktopStorageSession, level: Int, proof: VaultSession): Admission = try {
        walletLayer.value.onLevelNarrowing()
        Admission.Narrowing(open, open.narrowTo(level, proof), level)
    } catch (e: Exception) {
        logger.w(e) { "Narrowing the storage session failed" }
        Admission.Switching(open, proof)
    }

    private suspend fun complete(admission: Admission): UnlockOutcome = when (admission) {
        is Admission.Done -> admission.outcome
        is Admission.Narrowing -> completeNarrowing(admission)
        is Admission.Switching -> completeSwitch(admission)
    }

    private suspend fun completeNarrowing(narrowing: Admission.Narrowing): UnlockOutcome {
        val open = narrowing.open
        try {
            // Before the screen opens: it must never show the accounts of the level above.
            walletLayer.value.onLevelNarrowed(narrowing.level)
        } catch (e: Exception) {
            logger.w(e) { "The wallet did not follow the level change" }
            closeWallet()
            mutex.withLock { if (session.value === open) closeSession() }
            return UnlockOutcome.StorageUnavailable
        }
        levelRelease = releaseScope.launch { releaseLevels(narrowing) }
        return mutex.withLock {
            if (session.value !== open) return@withLock UnlockOutcome.Failed
            _state.value = DesktopVaultState.Unlocked(open)
            UnlockOutcome.Success
        }
    }

    /** An adapter that does not stop must not keep the keys of the level above alive. */
    private suspend fun releaseLevels(narrowing: Admission.Narrowing) {
        awaitWalletRelease(narrowing.open.accountIds)
        mutex.withLock { narrowing.retired.close() }
    }

    private suspend fun completeSwitch(switching: Admission.Switching): UnlockOutcome {
        // Outside the mutex: stopping a kit may need it.
        closeWallet()
        awaitWalletRelease(emptySet())
        return mutex.withLock {
            if (session.value !== switching.open) {
                switching.vaultSession.close()
                return@withLock UnlockOutcome.Failed
            }
            closeSession()
            startSession(switching.vaultSession, UnlockOutcome.Success, UnlockOutcome.StorageUnavailable)
        }
    }

    /** A release left running could clean up what the next session loads; the session close covers its levels. */
    private suspend fun closeWallet() {
        releaseScope.coroutineContext.job.children.toList().forEach { it.cancelAndJoin() }
        walletLayer.value.onSessionClosing()
    }

    private suspend fun awaitWalletRelease(keptAccountIds: Set<String>) {
        val released = withTimeoutOrNull(WALLET_RELEASE_TIMEOUT) { walletLayer.value.awaitReleased(keptAccountIds) }
        if (released == null) logger.w { "The wallet did not release the accounts in time" }
    }

    /** The slot check under the shared lockout counter; a correct password hands over its vault session. */
    private fun checkPassword(password: CharArray, accepts: (VaultSession) -> Boolean = { true }): PasswordCheck {
        // Covers a request queued on the mutex before the lockout began.
        (lockoutManager.currentState as? LockoutState.Locked)?.let { return PasswordCheck.LockedOut(it.until) }
        return when (val result = passwordVault.unlock(password)) {
            is UnlockResult.Success -> if (accepts(result.session)) {
                passwordAccepted(result.session)
            } else {
                result.session.close()
                failedAttempt()
            }
            UnlockResult.WrongPassword -> failedAttempt()
            UnlockResult.Corrupted -> PasswordCheck.Corrupted
            UnlockResult.LegacyFormat -> PasswordCheck.LegacyFormat
        }
    }

    private fun failedAttempt(): PasswordCheck.Rejected {
        lockoutManager.didFailUnlock()
        return when (val lockout = lockoutManager.currentState) {
            is LockoutState.Locked -> PasswordCheck.LockedOut(lockout.until)
            is LockoutState.Unlocked -> PasswordCheck.Wrong(lockout.attemptsLeft)
        }
    }

    /** The counter reset writes to disk, so its failure must not leave the fresh vault session open. */
    private fun passwordAccepted(vaultSession: VaultSession): PasswordCheck {
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
        if (opened == null) {
            _state.value = DesktopVaultState.Locked
            return failure
        }
        publish(opened)
        return success
    }

    /** Missing SQLCipher natives surface as a LinkageError; that is an open failure like any other. */
    private fun openStorage(vaultSession: VaultSession): DesktopStorageSession? {
        val cause = try {
            return DesktopStorageSession.open(vaultSession, environment)
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
        val closing = session.value ?: return
        session.value = null
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
        if (!deleteStorageFiles()) {
            logger.w { "Storage files outlived the wipe; the next create() deletes them" }
        }
        preferencesReset.applySystemLanguage()
        _state.value = DesktopVaultState.NoVault
        return WipeOutcome.SUCCESS
    }

    private fun deleteResidualData(): Boolean = delete(files.slotFiles()) && deleteStorageFiles()

    private fun deleteStorageFiles(): Boolean {
        val databaseDeleted = delete(files.databaseFiles())
        val vaultDeleted = files.vaultDirectory.deleteRecursively()
        return files.kitsDirectory.deleteRecursively() && vaultDeleted && databaseDeleted
    }

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

    private companion object {
        val WALLET_RELEASE_TIMEOUT = 30.seconds
    }
}

/** What an accepted password leads to; decided under the mutex, finished outside it. */
private sealed interface Admission {
    class Done(val outcome: UnlockOutcome) : Admission
    class Narrowing(val open: DesktopStorageSession, val retired: AutoCloseable, val level: Int) : Admission
    class Switching(val open: DesktopStorageSession, val vaultSession: VaultSession) : Admission
}

private sealed interface PasswordCheck {
    class Correct(val vaultSession: VaultSession) : PasswordCheck

    sealed interface Rejected : PasswordCheck
    class Wrong(val attemptsLeft: Int?) : Rejected
    class LockedOut(val until: Date) : Rejected
    data object Corrupted : Rejected
    data object LegacyFormat : Rejected
}

private fun PasswordCheck.Rejected.outcome(): UnlockOutcome = when (this) {
    is PasswordCheck.Wrong -> UnlockOutcome.WrongPassword(attemptsLeft)
    is PasswordCheck.LockedOut -> UnlockOutcome.LockedOut(until)
    PasswordCheck.Corrupted -> UnlockOutcome.Corrupted
    PasswordCheck.LegacyFormat -> UnlockOutcome.LegacyFormat
}
