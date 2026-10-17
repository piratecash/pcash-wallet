package cash.p.terminal.shared.desktop.vault

import cash.p.terminal.vault.PasswordVault
import cash.p.terminal.vault.UnlockResult
import cash.p.terminal.vault.VaultSession
import io.horizontalsystems.core.ILockoutStorage
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.coroutines.CoroutineContext
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Dispatches nothing until [flush], so a test can park an operation inside the vault mutex. */
private class ParkingDispatcher : CoroutineDispatcher() {
    private val parked = ArrayDeque<Runnable>()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        parked += block
    }

    fun flush() {
        while (parked.isNotEmpty()) parked.removeFirst().run()
    }
}

private class FailingResetLockoutStorage(private val storage: ILockoutStorage) : ILockoutStorage {
    override var failedAttempts: Int?
        get() = storage.failedAttempts
        set(value) {
            if (value == null) throw IOException("settings are not writable")
            storage.failedAttempts = value
        }

    override var lockoutUptime: Long?
        get() = storage.lockoutUptime
        set(value) {
            storage.lockoutUptime = value
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopVaultCancellationTest {

    private val fixture = DesktopVaultFixture()

    @AfterTest
    fun tearDown() = fixture.tearDown()

    @Test
    fun unlock_cancelledWhileQueuedOnTheMutex_wipesThePassword() = runTest {
        val parking = ParkingDispatcher()
        val vault = fixture.vault(passwordVault = slotVault(), io = parking)
        val queuedPassword = password(NEW_PASSWORD)

        val holding = launch { vault.unlock(password()) }
        runCurrent()
        val queued = launch { vault.unlock(queuedPassword) }
        runCurrent()
        queued.cancel()
        runCurrent()
        // The holding operation has to be let go first: a parked coroutine cannot even be cancelled.
        parking.flush()
        runCurrent()
        holding.join()

        assertTrue(queuedPassword.all { it == '\u0000' }, "queued password was left in memory")
        assertTrue(queued.isCancelled)
    }

    @Test
    fun unlock_cancellationInsideTheOperation_isRethrownInsteadOfReportedAsFailure() = runTest {
        val passwordVault = slotVault()
        every { passwordVault.unlock(any()) } throws CancellationException("cancelled")
        val vault = fixture.vault(passwordVault = passwordVault)

        assertFailsWith<CancellationException> { vault.unlock(password()) }
    }

    @Test
    fun lock_cancelledBeforeTheCloseRuns_closesTheSessionAnyway() = runTest {
        val parking = ParkingDispatcher()
        val vault = fixture.vault(io = parking)
        val create = launch { vault.create(password()) }
        runCurrent()
        parking.flush()
        runCurrent()
        create.join()
        val session = (vault.state.value as DesktopVaultState.Unlocked).session

        val locking = launch { vault.lock() }
        runCurrent()
        locking.cancel()
        runCurrent()
        parking.flush()
        runCurrent()

        assertEquals(null, session.secretFieldsKey(), "the session survived a cancelled lock")
        assertEquals(null, session.kitDatabaseKey("bitcoin", "account-1"))
    }

    @Test
    fun unlock_counterResetFails_closesTheVaultSessionAndStaysLocked() = runTest {
        val failingReset = DesktopVaultFixture(lockoutStorage = FailingResetLockoutStorage(FakeLockoutStorage()))
        try {
            failingReset.lockoutStorage.failedAttempts = 1
            val vaultSession = mockk<VaultSession>(relaxed = true)
            val passwordVault = slotVault()
            every { passwordVault.unlock(any()) } returns UnlockResult.Success(vaultSession)
            val vault = failingReset.vault(passwordVault = passwordVault)

            assertEquals(UnlockOutcome.Failed, vault.unlock(password()))

            assertEquals(DesktopVaultState.Locked, vault.state.value)
            verify { vaultSession.close() }
        } finally {
            failingReset.tearDown()
        }
    }

    private fun slotVault(): PasswordVault = mockk(relaxed = true) {
        every { exists() } returns true
        every { unlock(any()) } returns UnlockResult.WrongPassword
    }
}
