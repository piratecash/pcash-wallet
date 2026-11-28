package cash.p.terminal.shared.desktop.journal

import cash.p.terminal.feature.logging.settings.LoginJournalSecrets
import cash.p.terminal.modules.pin.core.ILockoutManager
import cash.p.terminal.modules.pin.core.LockoutState
import cash.p.terminal.shared.desktop.vault.DesktopStorageSession
import cash.p.terminal.shared.desktop.vault.DesktopVault
import cash.p.terminal.vault.PasswordVault
import cash.p.terminal.wallet.managers.ITransactionHiddenManager
import co.touchlab.kermit.Logger
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext

enum class JournalSecretOutcome { SUCCESS, WRONG_PASSWORD, LOCKED_OUT, PASSWORD_IN_USE, FAILED }

/**
 * The journal password and the delete-contacts password of the session root. Neither may equal a vault password,
 * and a wrong one counts into the lockout of the lock screen. Every operation wipes the passwords handed to it.
 */
class DesktopLoginJournalSecrets(
    private val vault: DesktopVault,
    private val passwordVault: PasswordVault,
    private val lockoutManager: ILockoutManager,
    private val dispatcherProvider: DispatcherProvider,
    private val transactionHiddenManager: ITransactionHiddenManager,
) : LoginJournalSecrets {
    private val changed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override val changes: Flow<Unit> = changed

    override fun isJournalSecretSet(): Boolean = vault.openSession()?.rootSafe?.let(JournalPassword::isSet) ?: false

    /** Read from what the lock screen acts on: on exactly while the published tag is one this level put there. */
    override fun isDeleteContactsSecretSet(): Boolean = vault.openSession()?.let(::tagsInForce)?.isNotEmpty() ?: false

    override fun disableJournalSecret() = change { JournalPassword.clear(it.rootSafe) }

    override fun disableDeleteContactsSecret() = change(::disableErase)

    suspend fun setJournalPassword(password: CharArray) = operation(password) { session ->
        unlessTaken(session, password, eraseSetter = false) {
            JournalPassword.set(session.rootSafe, password, passwordVault)
        }
    }

    suspend fun verifyJournalPassword(password: CharArray) = operation(password) { session ->
        checked { JournalPassword.matches(session.rootSafe, password, passwordVault) } ?: JournalSecretOutcome.SUCCESS
    }

    suspend fun setDeleteContactsPassword(password: CharArray) = operation(password) { session ->
        unlessTaken(session, password, eraseSetter = true) { installEraseTag(session, password) }
    }

    /** As on Android, turning it off takes the delete-contacts password itself. */
    suspend fun disableDeleteContactsPassword(password: CharArray) = operation(password) { session ->
        checked { passwordVault.matchesEraseTag(password, session.rootSlot) } ?: run {
            disableErase(session)
            JournalSecretOutcome.SUCCESS
        }
    }

    suspend fun changeDeleteContactsPassword(current: CharArray, new: CharArray) = operation(current, new) { session ->
        checked { passwordVault.matchesEraseTag(current, session.rootSlot) }
            ?: unlessTaken(session, new, eraseSetter = true) { installEraseTag(session, new) }
    }

    /**
     * The copy goes in beside the one in force before the tag is published, so after a failure at any step the
     * switch still shows what the lock screen does with the old password or the new one.
     */
    private fun installEraseTag(session: DesktopStorageSession, password: CharArray) {
        val tag = passwordVault.eraseTag(password, session.rootSlot)
        EraseTagCopies.write(session.rootSafe, tagsInForce(session) + tag)
        passwordVault.publishEraseTag(session.rootSlot, tag)
        tidying { EraseTagCopies.write(session.rootSafe, listOf(tag)) }
    }

    /** Off the moment the tag is noise; a copy left behind matches nothing. */
    private fun disableErase(session: DesktopStorageSession) {
        passwordVault.clearEraseTag(session.rootSlot)
        tidying { EraseTagCopies.clear(session.rootSafe) }
    }

    private fun tagsInForce(session: DesktopStorageSession): List<ByteArray> =
        EraseTagCopies.read(session.rootSafe).filter { passwordVault.eraseTagEquals(session.rootSlot, it) }

    private inline fun unlessTaken(
        session: DesktopStorageSession,
        password: CharArray,
        eraseSetter: Boolean,
        apply: () -> Unit,
    ): JournalSecretOutcome {
        // A level that did not open could hold a verifier of the password.
        if (!session.chain.isComplete) return JournalSecretOutcome.FAILED
        val eraseOwner = session.chain.root.takeIf { eraseSetter }
        if (passwordVault.opensAny(password) ||
            session.levels.matchesStoredVerifier(password, eraseOwner) ||
            isTransactionsHidePassword(password)
        ) {
            return JournalSecretOutcome.PASSWORD_IN_USE
        }
        apply()
        return JournalSecretOutcome.SUCCESS
    }

    private fun isTransactionsHidePassword(password: CharArray): Boolean =
        transactionHiddenManager.transactionHiddenFlow.value.transactionAutoHidePinExists &&
            transactionHiddenManager.isPinMatches(password.concatToString())

    /** Null when [matches]; otherwise the lock screen's lockout answers. */
    private inline fun checked(matches: () -> Boolean): JournalSecretOutcome? {
        if (lockoutManager.currentState is LockoutState.Locked) return JournalSecretOutcome.LOCKED_OUT
        if (matches()) {
            lockoutManager.dropFailedAttempts()
            return null
        }
        lockoutManager.didFailUnlock()
        return if (lockoutManager.currentState is LockoutState.Locked) {
            JournalSecretOutcome.LOCKED_OUT
        } else {
            JournalSecretOutcome.WRONG_PASSWORD
        }
    }

    /**
     * Under the vault lock: the vault file and the erase tags take one writer at a time. Readers are told on every
     * exit, since a step may have landed even when the caller was cancelled or a later step failed.
     */
    private suspend fun operation(
        vararg passwords: CharArray,
        block: (DesktopStorageSession) -> JournalSecretOutcome,
    ): JournalSecretOutcome = try {
        withContext(dispatcherProvider.io) {
            vault.exclusive { vault.openSession()?.let(block) ?: JournalSecretOutcome.FAILED }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logger.w(e) { "Journal secret operation failed" }
        JournalSecretOutcome.FAILED
    } finally {
        passwords.forEach { it.fill('\u0000') }
        changed.tryEmit(Unit)
    }

    private fun change(block: (DesktopStorageSession) -> Unit) {
        try {
            vault.exclusive { vault.openSession()?.let(block) }
        } finally {
            changed.tryEmit(Unit)
        }
    }
}

private val logger = Logger.withTag("LoginJournalSecrets")

/** For a step after which the state is already right either way. */
private inline fun tidying(block: () -> Unit) = try {
    block()
} catch (e: Exception) {
    logger.w(e) { "Removing a stale erase tag copy failed" }
}
