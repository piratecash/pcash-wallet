package cash.p.terminal.core.managers

import cash.p.terminal.wallet.AccountDeletionBlockedException
import cash.p.terminal.wallet.AccountDeletionPreflight
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.IEnabledWalletStorage
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

class BeamAccountDeletionPreflight(
    private val storageLocator: BeamStorageLocator,
    private val keyProvider: Lazy<BeamDatabaseKeyProvider>,
    private val enabledWalletStorage: IEnabledWalletStorage,
    private val dispatcherProvider: DispatcherProvider,
    private val deletionState: BeamDeletionState,
    private val owner: Lazy<BeamSessionOwner>,
    private val adapterManager: Lazy<IAdapterManager>,
) : AccountDeletionPreflight {
    private val nativeTokenId = TokenQuery(BlockchainType.Beam, TokenType.Native).id
    private val cleanupMutex = Mutex()

    override suspend fun ensureCanDelete(accountIds: List<String>) = guarded {
        accountIds.forEach { id ->
            BeamNetwork.entries.forEach { network ->
                storageLocator.validatedPath(storageLocator.storageId(id, network))
            }
        }
    }

    override suspend fun cleanupDeleted(accountIds: List<String>) = guarded {
        cleanupMutex.withLock {
            deletionState.ensureDeletionPending(accountIds)
            // During an explicit reset the per-account cleanup AccountCleaner requests IS the reset
            // cleanup, and it must stay best-effort: the key wrappers are already shredded, so
            // nothing here may block or fail the reset; the retained marker and resumePendingReset()
            // finish any leftover. Ordinary account deletion stays fail-closed.
            if (deletionState.resetPending) {
                cleanupReset()
            } else {
                cleanup(accountIds, accountIds.flatMap { id ->
                    BeamNetwork.entries.map { storageLocator.storageId(id, it) }
                }.toSet())
            }
        }
    }

    override suspend fun prepareExplicitReset() = guarded {
        cleanupMutex.withLock {
            deletionState.beginReset()
            cleanupReset()
        }
    }

    override suspend fun resumePendingReset() = guarded {
        cleanupMutex.withLock {
            if (deletionState.resetPending) {
                deletionState.beginReset()
                cleanupReset()
                if (deletionState.accountIds.isEmpty()) finishExplicitReset()
            }
        }
    }

    // Barrier for the global clear: every key wrapper must be gone, which makes any BEAM file that
    // survived the erase an unrecoverable blob. Leftover files are finished by resumePendingReset().
    override suspend fun ensureExplicitResetCleaned() = guarded {
        check(deletionState.resetPending && !keyProvider.value.hasAnyKey())
    }

    // Dropping the marker requires the fully clean state; otherwise it is kept for the next startup.
    override suspend fun finishExplicitReset() = guarded {
        check(deletionState.resetPending && !storageLocator.hasAnyData() && !keyProvider.value.hasAnyKey())
        deletionState.finishReset()
    }

    // Synchronous by contract: only prefs/file/enabled-wallets reads, no IO dispatch needed.
    override fun ensureCanReset() = guardedSync {
        val blocked = when {
            deletionState.resetPending || deletionState.hasRestoreIntent -> true
            storageLocator.hasAnyData() || keyProvider.value.hasAnyKey() -> true
            else -> enabledWalletStorage.enabledWallets.any { it.tokenQueryId == nativeTokenId }
        }
        if (blocked) throw AccountDeletionBlockedException()
    }

    // The explicit reset is a duress operation: it must finish quickly and must never be blocked by
    // the native session. Crypto-shred first, then everything below only removes already-dead bytes.
    private suspend fun cleanupReset() {
        val ids = (deletionState.accountIds + listOfNotNull(owner.value.current?.accountId)).distinct()
        shredKeys()
        stopDetached(ids)
        eraseBestEffort()
        clearResetLinkageBestEffort()
    }

    // Nothing after the shred may fail the reset. ResetUseCase.purgeDatabases() runs
    // appDatabase.clearAllTables() right after and removes the same restore-setting rows, and
    // finishExplicitReset() still verifies !hasRestoreIntent through finishReset(), so anything that
    // survives keeps the marker for resumePendingReset(). Account deletion stays fail-closed.
    private fun clearResetLinkageBestEffort() {
        try {
            deletionState.clearResetLinkage()
        } catch (error: Exception) {
            Timber.w(error, "BEAM reset linkage removal deferred to the global clear")
        }
    }

    // Fail-closed: a wrapper that cannot be removed leaves its database decryptable. Both
    // ensureExplicitResetCleaned and finishExplicitReset require !hasAnyKey(), so a surviving wrapper
    // makes them reject and the reset cannot advance to the global clear.
    private fun shredKeys() {
        keyProvider.value.storageIds().toList().forEach { keyProvider.value.shredStorageKey(it) }
    }

    // Why this cannot deadlock on BeamSessionOwner.mutex: this coroutine never takes that mutex. It
    // only joins a job that does, and the join is bounded. A `withTimeoutOrNull` placed around the
    // stop itself would not help - drainDeleted runs under NonCancellable, so the timeout could not
    // interrupt it and the reset would still wait for whoever holds the mutex (an in-flight
    // withSession/acquire, possibly a long native call). Running the stop in the application scope
    // gives it a Job independent of this one, so the bounded join can give up while the stop keeps
    // draining in the background. Fencing is safe to do inline: it only takes the plain monitor.
    private suspend fun stopDetached(accountIds: List<String>) {
        owner.value.fenceDeletion(accountIds)
        val stop = dispatcherProvider.applicationScope.launch {
            try {
                adapterManager.value.stopAdapters(accountIds, BlockchainType.Beam)
                owner.value.drainDeleted(accountIds)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // Do not log the throwable itself: SDK-backed exceptions may contain secrets (same
                // rationale as BeamAdapter's send-reconciliation catch).
                Timber.w("BEAM session stop failed during explicit reset: %s", error.javaClass.simpleName)
            }
        }
        if (withTimeoutOrNull(STOP_TIMEOUT_MILLIS) { stop.join() } == null) {
            Timber.w("BEAM session stop is still running; explicit reset continues without it")
        }
    }

    // Keys are already gone, so a leftover file is an unreadable blob: log it and let
    // resumePendingReset() remove it at the next startup instead of failing the reset.
    private fun eraseBestEffort() {
        val storageIds = try {
            storageLocator.storageIds()
        } catch (error: Exception) {
            Timber.w(error, "Unable to enumerate BEAM storage during explicit reset")
            return
        }
        storageIds.forEach { id ->
            try {
                storageLocator.erase(id)
            } catch (error: Exception) {
                Timber.w(error, "BEAM storage removal deferred to startup cleanup")
            }
        }
    }

    private suspend fun cleanup(accountIds: List<String>, storageIds: Set<String>) {
        stopAndDrain(accountIds)
        erase(storageIds)
        deletionState.clearLinkage(accountIds)
    }

    private suspend fun stopAndDrain(accountIds: List<String>) {
        owner.value.fenceDeletion(accountIds)
        adapterManager.value.stopAdapters(accountIds, BlockchainType.Beam)
        owner.value.drainDeleted(accountIds)
    }

    private fun erase(storageIds: Set<String>) {
        storageIds.forEach { id ->
            storageLocator.erase(id)
            keyProvider.value.removeStorageKey(id)
        }
    }

    private suspend fun guarded(action: suspend () -> Unit) = withContext(dispatcherProvider.io) {
        try {
            action()
        } catch (error: CancellationException) {
            throw error
        } catch (error: AccountDeletionBlockedException) {
            throw error
        } catch (error: Exception) {
            throw AccountDeletionBlockedException(error)
        }
    }

    private fun guardedSync(action: () -> Unit) {
        try {
            action()
        } catch (error: AccountDeletionBlockedException) {
            throw error
        } catch (error: Exception) {
            throw AccountDeletionBlockedException(error)
        }
    }

    private companion object {
        // Long enough for an idle session to close, short enough to keep a duress reset responsive.
        const val STOP_TIMEOUT_MILLIS = 3_000L
    }
}
