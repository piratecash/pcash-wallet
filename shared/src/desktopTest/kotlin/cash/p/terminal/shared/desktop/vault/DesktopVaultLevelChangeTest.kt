package cash.p.terminal.shared.desktop.vault

import cash.p.terminal.core.storage.AccountDaos
import cash.p.terminal.core.storage.observeAcrossAccounts
import cash.p.terminal.shared.desktop.vault.VaultLevelsFixture.Companion.DURESS_ACCOUNT
import cash.p.terminal.shared.desktop.vault.VaultLevelsFixture.Companion.MAIN_ACCOUNT
import cash.p.terminal.shared.desktop.vault.VaultLevelsFixture.Companion.NESTED_ACCOUNT
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DesktopVaultLevelChangeTest {

    @Test
    fun unlock_duressUnderMainLock_narrowsOneSessionAndClosesTheLevelAbove() = levelsScenario { fixture ->
        fixture.mainWithDuress()
        val session = fixture.session
        val mainSafe = session.chain.root
        val mainSafeKeyCopy = mainSafe.link.key.copyOf()
        val mainPremiumKey = mainSafe.meta.premiumKey
        val mainStoreKey = fixture.storeKey(MAIN_ACCOUNT)
        val duressStoreKey = fixture.storeKey(DURESS_ACCOUNT)
        fixture.vault.lock()

        assertEquals(UnlockOutcome.Success, fixture.vault.unlock(password(DURESS_PASSWORD)))
        fixture.vault.awaitLevelRelease()

        assertSame(session, fixture.vault.openSession())
        assertEquals(1, session.rootLevel)
        assertEquals(listOf(1), fixture.walletLayer.narrowedTo)
        assertEquals(0, fixture.walletLayer.sessionClosings)
        assertEquals(setOf(DURESS_ACCOUNT), session.accountIds)
        assertEquals(mapOf(DURESS_ACCOUNT to 1), fixture.visibleAccounts())
        assertNull(session.account(MAIN_ACCOUNT))
        verify { fixture.driverFactory.driverFor(mainStoreKey).close() }
        verify { fixture.driverFactory.driverFor(mainSafeKeyCopy).close() }
        verify { fixture.driverFactory.driverFor(mainPremiumKey).close() }
        verify(exactly = 0) { fixture.driverFactory.driverFor(duressStoreKey).close() }
        assertTrue(mainSafe.link.key.all { it == 0.toByte() })
    }

    @Test
    fun unlock_narrowingFailsRightAfterTheChainSplit_closesTheUpperSafeOnTheColdSwitch() = levelsScenario { fixture ->
        fixture.mainWithDuress()
        val session = fixture.session
        val mainSafe = session.chain.root
        val mainSafeKeyCopy = mainSafe.link.key.copyOf()
        val mainPremiumKey = mainSafe.meta.premiumKey
        fixture.checkpoints.onReached = {
            if (it == StorageCheckpoint.NARROWING_CHAIN_SPLIT) throw IOException("a read of the kept safe failed")
        }
        fixture.vault.lock()

        assertEquals(UnlockOutcome.Success, fixture.vault.unlock(password(DURESS_PASSWORD)))

        assertNotSame(session, fixture.vault.openSession())
        assertEquals(1, fixture.walletLayer.sessionClosings)
        verify { fixture.driverFactory.driverFor(mainSafeKeyCopy).close() }
        verify { fixture.driverFactory.driverFor(mainPremiumKey).close() }
        assertTrue(mainSafe.link.key.all { it == 0.toByte() })
    }

    @Test
    fun unlock_coldSwitchDuringAnEarlierRelease_thatReleaseEndsBeforeTheNewSessionOpens() = levelsScenario { fixture ->
        fixture.mainWithDuress()
        val oldSession = fixture.session
        val mainSafe = oldSession.chain.root
        val reachedCleanup = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        val staleCleanups = CopyOnWriteArrayList<Set<String>>()
        fixture.walletLayer.onReleased = { kept ->
            if (kept.isNotEmpty()) {
                reachedCleanup.complete(Unit)
                resume.await()
                staleCleanups += kept
            }
        }
        fixture.vault.lock()
        assertEquals(UnlockOutcome.Success, fixture.vault.unlock(password(DURESS_PASSWORD)))
        withTimeout(WAIT_MS) { reachedCleanup.await() }

        fixture.vault.lock()
        assertEquals(UnlockOutcome.Success, fixture.vault.unlock(password(MAIN_PASSWORD)))
        resume.complete(Unit)
        delay(SETTLE_MS)

        assertNotSame(oldSession, fixture.vault.openSession())
        assertEquals(emptyList(), staleCleanups.toList())
        assertTrue(mainSafe.link.key.all { it == 0.toByte() }, "the ended release left its levels open")
    }

    @Test
    fun unlock_duressUnderMainLock_keepsUpperStoreOpenOutOfViewUntilTheWalletReleasedIt() = levelsScenario { fixture ->
        fixture.mainWithDuress()
        val session = fixture.session
        val released = CompletableDeferred<Unit>()
        fixture.walletLayer.onReleased = { released.await() }
        fixture.vault.lock()

        assertEquals(UnlockOutcome.Success, fixture.vault.unlock(password(DURESS_PASSWORD)))

        assertIs<DesktopVaultState.Unlocked>(fixture.vault.state.value)
        assertEquals(listOf(setOf(DURESS_ACCOUNT)), fixture.walletLayer.keptAccountIds)
        assertEquals(setOf(DURESS_ACCOUNT), session.accountIds)
        assertEquals(1, session.accounts.size)
        assertNull(session.levelOf(MAIN_ACCOUNT))
        assertEquals(mapOf(DURESS_ACCOUNT to 1), fixture.visibleAccounts())
        assertNotNull(session.account(MAIN_ACCOUNT))

        released.complete(Unit)
        fixture.vault.awaitLevelRelease()

        assertNull(session.account(MAIN_ACCOUNT))
    }

    @Test
    fun unlock_nestedDuressFromDuress_narrowsTheSameSessionAgain() = levelsScenario { fixture ->
        fixture.mainWithNestedDuress()
        val session = fixture.session
        val duressSafe = session.chain.safes[1]
        fixture.vault.lock()
        fixture.vault.unlock(password(DURESS_PASSWORD))
        fixture.vault.awaitLevelRelease()
        fixture.vault.lock()

        assertEquals(UnlockOutcome.Success, fixture.vault.unlock(password(NESTED_PASSWORD)))
        fixture.vault.awaitLevelRelease()

        assertSame(session, fixture.vault.openSession())
        assertEquals(2, session.rootLevel)
        assertEquals(listOf(1, 2), fixture.walletLayer.narrowedTo)
        assertEquals(mapOf(NESTED_ACCOUNT to 2), fixture.visibleAccounts())
        assertNull(session.account(DURESS_ACCOUNT))
        assertTrue(duressSafe.link.key.all { it == 0.toByte() })
    }

    @Test
    fun unlock_mainPasswordOverNarrowedSession_switchesColdToAFreshSession() = levelsScenario { fixture ->
        fixture.mainWithDuress()
        val narrowed = fixture.session
        fixture.vault.lock()
        fixture.vault.unlock(password(DURESS_PASSWORD))
        fixture.vault.awaitLevelRelease()
        fixture.vault.lock()

        assertEquals(UnlockOutcome.Success, fixture.vault.unlock(password(MAIN_PASSWORD)))

        assertNotSame(narrowed, fixture.session)
        assertNull(narrowed.sharedNamesSecret())
        assertEquals(0, fixture.session.rootLevel)
        assertEquals(mapOf(MAIN_ACCOUNT to 0, DURESS_ACCOUNT to 1), fixture.visibleAccounts())
        assertEquals(1, fixture.walletLayer.sessionClosings)
        assertEquals(emptySet(), fixture.walletLayer.keptAccountIds.last())
        assertSame(fixture.session, assertIs<DesktopVaultState.Unlocked>(fixture.vault.state.value).session)
    }

    @Test
    fun unlock_mainAndHidden_switchColdBothWaysAndNeverSeeEachOther() = levelsScenario { fixture ->
        fixture.create()
        fixture.createHidden()
        fixture.addAccount(MAIN_ACCOUNT)
        val main = fixture.session
        fixture.vault.lock()

        assertEquals(UnlockOutcome.Success, fixture.vault.unlock(password(HIDDEN_PASSWORD)))

        val hidden = fixture.session
        assertNotSame(main, hidden)
        assertNull(main.sharedNamesSecret())
        assertEquals(emptyMap(), fixture.visibleAccounts())
        assertEquals(DuressAvailability.UNAVAILABLE, fixture.vault.duressAvailability())
        fixture.addAccount(DURESS_ACCOUNT)
        fixture.vault.lock()

        assertEquals(UnlockOutcome.Success, fixture.vault.unlock(password(MAIN_PASSWORD)))

        assertNull(hidden.sharedNamesSecret())
        assertEquals(mapOf(MAIN_ACCOUNT to 0), fixture.visibleAccounts())
        assertEquals(2, fixture.walletLayer.sessionClosings)
    }

    @Test
    fun unlock_samePasswordOverNarrowedSession_resumesWithoutTouchingTheWallet() = levelsScenario { fixture ->
        fixture.mainWithDuress()
        val session = fixture.session
        fixture.vault.lock()
        fixture.vault.unlock(password(DURESS_PASSWORD))
        fixture.vault.awaitLevelRelease()
        fixture.vault.lock()

        assertEquals(UnlockOutcome.Success, fixture.vault.unlock(password(DURESS_PASSWORD)))

        assertSame(session, fixture.vault.openSession())
        assertEquals(1, session.rootLevel)
        assertEquals(listOf(1), fixture.walletLayer.narrowedTo)
        assertEquals(0, fixture.walletLayer.sessionClosings)
    }

    @Test
    fun unlock_walletCannotFollowTheNarrowing_closesTheSessionAndTheNextTryStartsCold() = levelsScenario { fixture ->
        fixture.mainWithDuress()
        val session = fixture.session
        fixture.walletLayer.onNarrowed = { error("no storage") }
        fixture.vault.lock()

        assertEquals(UnlockOutcome.StorageUnavailable, fixture.vault.unlock(password(DURESS_PASSWORD)))

        assertNull(fixture.vault.openSession())
        assertNull(session.sharedNamesSecret())
        assertEquals(DesktopVaultState.Locked, fixture.vault.state.value)
        assertEquals(1, fixture.walletLayer.sessionClosings)

        fixture.walletLayer.onNarrowed = {}
        assertEquals(UnlockOutcome.Success, fixture.vault.unlock(password(DURESS_PASSWORD)))
        assertEquals(0, fixture.session.rootLevel)
        assertEquals(mapOf(DURESS_ACCOUNT to 0), fixture.visibleAccounts())
    }

    @Test
    fun close_narrowingStillReleasing_closesTheRetiredLevelsToo() = levelsScenario { fixture ->
        fixture.mainWithDuress()
        val mainSafe = fixture.session.chain.root
        val mainStoreKey = fixture.storeKey(MAIN_ACCOUNT)
        val released = CompletableDeferred<Unit>()
        fixture.walletLayer.onReleased = { released.await() }
        fixture.vault.lock()
        fixture.vault.unlock(password(DURESS_PASSWORD))

        fixture.vault.close()

        verify { fixture.driverFactory.driverFor(mainStoreKey).close() }
        assertTrue(mainSafe.link.key.all { it == 0.toByte() })
        released.complete(Unit)
        fixture.vault.awaitLevelRelease()
    }

    @Test
    fun narrowedSession_levelBelowItsRoot_resolvesToTheRootOrToNothing() = levelsScenario { fixture ->
        fixture.mainWithDuress()
        fixture.vault.lock()
        fixture.vault.unlock(password(DURESS_PASSWORD))
        fixture.vault.awaitLevelRelease()
        val premium = DesktopPremiumDatabaseRouter(fixture.vault)
        val settings = DesktopLevelSettings(fixture.vault)

        assertEquals(1, fixture.router.rootLevel)
        assertEquals(1, premium.rootLevel)
        assertSame(fixture.router.level(1), fixture.router.level(0))
        assertSame(premium.forLevel(1), premium.forLevel(0))
        assertNull(fixture.session.safeOrNull(0))
        settings.setFlag(0, FLAG, true)
        assertNull(settings.flag(0, FLAG))
        assertNull(settings.flag(1, FLAG))
        settings.setFlag(1, FLAG, true)
        assertEquals(true, settings.flag(1, FLAG))
    }

    @Test
    fun narrowedSession_newAccountAndNewLevel_landInItsOwnChainAtAbsoluteLevels() = levelsScenario { fixture ->
        fixture.mainWithDuress()
        fixture.vault.lock()
        fixture.vault.unlock(password(DURESS_PASSWORD))
        fixture.vault.awaitLevelRelease()

        assertEquals(DuressAvailability.NOT_SET, fixture.vault.duressAvailability())
        fixture.setDuress(NESTED_PASSWORD)
        fixture.addAccount(NESTED_ACCOUNT)
        assertEquals(1, fixture.router.levelOf(NESTED_ACCOUNT))
        fixture.router.setLevel(listOf(NESTED_ACCOUNT), 2)
        assertEquals(2, fixture.router.levelOf(NESTED_ACCOUNT))

        fixture.reloginOk(NESTED_PASSWORD)
        assertEquals(mapOf(NESTED_ACCOUNT to 0), fixture.visibleAccounts())
        fixture.reloginOk(MAIN_PASSWORD)
        assertEquals(
            mapOf(MAIN_ACCOUNT to 0, DURESS_ACCOUNT to 1, NESTED_ACCOUNT to 2),
            fixture.visibleAccounts(),
        )
    }

    @Test
    fun verifyPassword_otherLevel_isAWrongPasswordThatCounts() = levelsScenario { fixture ->
        fixture.mainWithDuress()
        fixture.createHidden()

        assertEquals(UnlockOutcome.WrongPassword(4), fixture.vault.verifyPassword(password(DURESS_PASSWORD)))
        assertEquals(UnlockOutcome.WrongPassword(3), fixture.vault.verifyPassword(password(HIDDEN_PASSWORD)))
        assertEquals(UnlockOutcome.Success, fixture.vault.verifyPassword(password(MAIN_PASSWORD)))

        fixture.vault.lock()
        fixture.vault.unlock(password(DURESS_PASSWORD))
        fixture.vault.awaitLevelRelease()

        assertEquals(UnlockOutcome.WrongPassword(4), fixture.vault.verifyPassword(password(MAIN_PASSWORD)))
        assertEquals(UnlockOutcome.Success, fixture.vault.verifyPassword(password(DURESS_PASSWORD)))
    }

    @Test
    fun changePassword_oldPasswordOfAnotherRoot_isAWrongPasswordThatCounts() = levelsScenario { fixture ->
        fixture.create()
        fixture.createHidden()

        val outcome = fixture.vault.changePassword(password(HIDDEN_PASSWORD), password(NEW_PASSWORD))

        assertEquals(ChangePasswordOutcome.WRONG_PASSWORD, outcome)
        assertEquals(UnlockOutcome.WrongPassword(3), fixture.vault.verifyPassword(password("Not-a-password5")))
        assertEquals(UnlockOutcome.Success, fixture.vault.verifyPassword(password(MAIN_PASSWORD)))
    }

    @Test
    fun changePassword_newPasswordOfAnotherLevel_isInUseWithoutCountingAnAttempt() = levelsScenario { fixture ->
        fixture.mainWithDuress()
        fixture.createHidden()

        val taken = listOf(DURESS_PASSWORD, HIDDEN_PASSWORD)
            .map { fixture.vault.changePassword(password(MAIN_PASSWORD), password(it)) }

        assertEquals(List(2) { ChangePasswordOutcome.PASSWORD_IN_USE }, taken)
        assertEquals(UnlockOutcome.WrongPassword(4), fixture.vault.verifyPassword(password("Not-a-password5")))
        assertEquals(UnlockOutcome.Success, fixture.vault.verifyPassword(password(MAIN_PASSWORD)))
    }

    @Test
    fun changePassword_newPasswordEqualToTheOwnOne_succeedsAsOnAndroid() = levelsScenario { fixture ->
        fixture.create()

        val outcome = fixture.vault.changePassword(password(MAIN_PASSWORD), password(MAIN_PASSWORD))

        assertEquals(ChangePasswordOutcome.SUCCESS, outcome)
        assertEquals(UnlockOutcome.Success, fixture.vault.verifyPassword(password(MAIN_PASSWORD)))
    }

    @Test
    fun changePassword_lockedOut_reportsItEvenForTheRightPassword() = levelsScenario { fixture ->
        fixture.create()
        repeat(5) { fixture.vault.verifyPassword(password("Not-a-password5")) }

        val outcome = fixture.vault.changePassword(password(MAIN_PASSWORD), password(NEW_PASSWORD))

        assertEquals(ChangePasswordOutcome.LOCKED_OUT, outcome)
    }

    @Test
    fun changePassword_inNarrowedSession_resealsThatLevelInPlace() = levelsScenario { fixture ->
        fixture.mainWithDuress()
        fixture.vault.lock()
        fixture.vault.unlock(password(DURESS_PASSWORD))
        fixture.vault.awaitLevelRelease()
        val session = fixture.session

        val outcome = fixture.vault.changePassword(password(DURESS_PASSWORD), password(NEW_PASSWORD))

        assertEquals(ChangePasswordOutcome.SUCCESS, outcome)
        assertSame(session, fixture.vault.openSession())
        fixture.reloginOk(NEW_PASSWORD)
        assertEquals(mapOf(DURESS_ACCOUNT to 0), fixture.visibleAccounts())
        fixture.reloginOk(MAIN_PASSWORD)
        assertEquals(mapOf(MAIN_ACCOUNT to 0, DURESS_ACCOUNT to 1), fixture.visibleAccounts())
    }

    @Test
    fun settings_afterTheSessionIsGone_dropTheWriteWithoutThrowing() = levelsScenario { fixture ->
        fixture.create()
        fixture.addAccount(MAIN_ACCOUNT)
        val accountSettings = DesktopAccountSettings(fixture.vault)
        val levelSettings = DesktopLevelSettings(fixture.vault)

        fixture.vault.close()

        accountSettings.setStrings(FLAG, setOf(MAIN_ACCOUNT)) { it }
        levelSettings.setFlag(0, FLAG, true)
        assertEquals(emptySet(), accountSettings.strings(FLAG))
        assertNull(levelSettings.flag(0, FLAG))
    }

    @Test
    fun observeAcrossAccounts_narrowing_movesToTheKeptStoresWithoutFailingTheCollector() = levelsScenario { fixture ->
        fixture.mainWithDuress()
        fixture.writeSwap(MAIN_ACCOUNT)
        val failures = CopyOnWriteArrayList<Throwable>()
        val scope = collectorScope(failures)
        val counts = fixture.router.observeAcrossAccounts { pendingMultiSwapDao().count().map(::listOf) }
        scope.launch { counts.collect {} }
        withTimeout(WAIT_MS) { counts.first { it.sum() == 1 } }
        fixture.vault.lock()

        fixture.vault.unlock(password(DURESS_PASSWORD))
        fixture.vault.awaitLevelRelease()

        withTimeout(WAIT_MS) { counts.first { it.sum() == 0 } }
        fixture.writeSwap(DURESS_ACCOUNT)
        withTimeout(WAIT_MS) { counts.first { it.sum() == 1 } }
        scope.cancel()
        assertEquals(emptyList(), failures.toList())
    }

    @Test
    fun roomFlow_heldDirectlyOnAStoreThatClosesUnderWrites_doesNotFailItsCollector() = levelsScenario { fixture ->
        fixture.mainWithDuress()
        val failures = CopyOnWriteArrayList<Throwable>()
        val scope = collectorScope(failures)
        val store = fixture.store(MAIN_ACCOUNT)
        val firstValue = CompletableDeferred<Unit>()
        scope.launch { store.pendingMultiSwapDao().count().collect { firstValue.complete(Unit) } }
        withTimeout(WAIT_MS) { firstValue.await() }
        val writerStopped = scope.keepWritingUntilClosed(fixture, store)
        fixture.vault.lock()

        fixture.vault.unlock(password(DURESS_PASSWORD))
        fixture.vault.awaitLevelRelease()

        withTimeout(WAIT_MS) { writerStopped.join() }
        delay(SETTLE_MS)
        scope.cancel()
        assertEquals(emptyList(), failures.toList())
    }

    @Test
    fun observeAcrossAccounts_coldSwitchUnderWrites_doesNotFailTheCollector() = levelsScenario { fixture ->
        fixture.mainWithDuress()
        fixture.createHidden()
        val failures = CopyOnWriteArrayList<Throwable>()
        val scope = collectorScope(failures)
        val counts = fixture.router.observeAcrossAccounts { pendingMultiSwapDao().count().map(::listOf) }
        val firstValue = CompletableDeferred<Unit>()
        scope.launch { counts.collect { firstValue.complete(Unit) } }
        withTimeout(WAIT_MS) { firstValue.await() }
        val writerStopped = scope.keepWritingUntilClosed(fixture, fixture.store(MAIN_ACCOUNT))
        fixture.vault.lock()

        assertEquals(UnlockOutcome.Success, fixture.vault.unlock(password(HIDDEN_PASSWORD)))

        withTimeout(WAIT_MS) { writerStopped.join() }
        delay(SETTLE_MS)
        scope.cancel()
        assertEquals(emptyList(), failures.toList())
    }

    /** Keeps the flow re-querying while the store closes; a closed store failing the writer is what ends it. */
    private fun CoroutineScope.keepWritingUntilClosed(fixture: VaultLevelsFixture, store: AccountDaos): Job = launch {
        try {
            while (true) fixture.writeSwapTo(store, "row")
        } catch (e: IllegalStateException) {
            currentCoroutineContext().ensureActive()
        }
    }

    private fun collectorScope(failures: MutableList<Throwable>) = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, failure -> failures += failure },
    )

    private companion object {
        const val FLAG = "a-flag"
        const val WAIT_MS = 10_000L
        const val SETTLE_MS = 300L
    }
}
