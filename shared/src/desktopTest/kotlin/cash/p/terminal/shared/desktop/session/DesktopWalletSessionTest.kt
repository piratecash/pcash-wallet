package cash.p.terminal.shared.desktop.session

import cash.p.terminal.modules.contacts.ContactsRepository
import cash.p.terminal.core.ITermsManager
import cash.p.terminal.core.StorageLockedException
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.managers.AmlStatusManager
import cash.p.terminal.core.managers.BitcoinKitDatabaseManager
import cash.p.terminal.core.managers.OfflineModeManager
import cash.p.terminal.core.managers.TonConnectManager
import cash.p.terminal.core.managers.TransactionAdapterManager
import cash.p.terminal.core.managers.WalletServicesStarter
import cash.p.terminal.premium.domain.usecase.CheckPremiumUseCase
import cash.p.terminal.shared.desktop.DesktopLogGate
import cash.p.terminal.shared.desktop.DesktopWalletAccountHolders
import cash.p.terminal.shared.desktop.GatedLogsDao
import cash.p.terminal.shared.desktop.DesktopWalletSession
import cash.p.terminal.shared.desktop.vault.DesktopStorageSession
import cash.p.terminal.shared.desktop.vault.DesktopVault
import cash.p.terminal.shared.desktop.vault.DesktopVaultState
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.managers.UserManager
import io.horizontalsystems.core.storage.LogEntry
import io.horizontalsystems.core.storage.LogsDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopWalletSessionTest {

    private val vaultState = MutableStateFlow<DesktopVaultState>(DesktopVaultState.Locked)
    private val vault = mockk<DesktopVault> { every { state } returns vaultState }
    private val userManager = mockk<UserManager>(relaxed = true)
    private val accountManager = mockk<IAccountManager>(relaxed = true)
    private val termsManager = mockk<ITermsManager>(relaxed = true)
    private val contactsRepository = mockk<ContactsRepository>(relaxed = true)
    private val checkPremiumUseCase = mockk<CheckPremiumUseCase>(relaxed = true)
    private val walletServicesStarter = mockk<WalletServicesStarter>(relaxed = true)
    private val offlineModeManager = mockk<OfflineModeManager>(relaxed = true)
    private val lazyCheckPremiumUseCase = lazy { checkPremiumUseCase }
    private val adapterManager = mockk<IAdapterManager>(relaxed = true)
    private val lazyAdapterManager = lazy { adapterManager }
    private val tonConnectManager = mockk<TonConnectManager>(relaxed = true)
    private val lazyTonConnectManager = lazy { tonConnectManager }
    private val logGate = DesktopLogGate()
    private val logsDao = mockk<LogsDao>(relaxed = true)
    private val logs = GatedLogsDao(logsDao, logGate)

    @Test
    fun start_resumeOfOneSession_loadsTheWalletOnce() = runTest {
        startSession()
        val unlocked = unlocked()

        vaultState.value = unlocked
        vaultState.value = DesktopVaultState.Locked
        vaultState.value = unlocked

        verify(exactly = 1) { userManager.setUserLevel(0) }
        verify(exactly = 1) { checkPremiumUseCase.startWarmUp() }
        coVerify(exactly = 0) { checkPremiumUseCase.stopWarmUp() }
        verify(exactly = 0) { accountManager.unload() }
    }

    @Test
    fun start_newSession_readsItsOfflineFlagsBeforeItsAccounts() = runTest {
        startSession()
        vaultState.value = unlocked()

        coVerifyOrder {
            offlineModeManager.reload()
            userManager.setUserLevel(0)
        }
    }

    @Test
    fun start_anotherSession_loadsTheWalletAgain() = runTest {
        startSession()

        vaultState.value = unlocked()
        vaultState.value = DesktopVaultState.Locked
        vaultState.value = unlocked()

        verify(exactly = 2) { userManager.setUserLevel(0) }
        verify(exactly = 2) { checkPremiumUseCase.startWarmUp() }
    }

    @Test
    fun start_sessionAlreadyNarrowed_loadsItsRootLevelNotZero() = runTest {
        val session = mockk<DesktopStorageSession>(relaxed = true) { every { rootLevel } returns 1 }
        startSession()

        vaultState.value = DesktopVaultState.Unlocked(session)

        verify(exactly = 1) { userManager.setUserLevel(1) }
        verify(exactly = 0) { userManager.setUserLevel(0) }
    }

    @Test
    fun onLevelNarrowed_runningWarmUp_restartsItAroundTheLevelChange() = runTest {
        val session = startSession()
        vaultState.value = unlocked()

        session.onLevelNarrowed(1)

        coVerifyOrder {
            checkPremiumUseCase.stopWarmUp()
            userManager.setUserLevel(1)
            checkPremiumUseCase.startWarmUp()
        }
    }

    @Test
    fun onSessionClosing_openSession_dropsTheWalletButNotTheSharedContacts() = runTest {
        val session = startSession()
        vaultState.value = unlocked()

        session.onSessionClosing()

        coVerifyOrder {
            checkPremiumUseCase.stopWarmUp()
            accountManager.unload()
            userManager.resetUserLevel()
        }
        verify(exactly = 0) { contactsRepository.clear() }
    }

    @Test
    fun awaitReleased_beforeTheServicesStarted_neverBuildsTheAdapterManagers() = runTest {
        val session = startSession()

        session.awaitReleased(emptySet())

        assertFalse(lazyAdapterManager.isInitialized())
    }

    @Test
    fun start_unlockAfterALock_startsTheWalletServicesOnce() = runTest {
        startSession()

        vaultState.value = unlocked()
        vaultState.value = DesktopVaultState.Locked
        vaultState.value = unlocked()

        coVerify(exactly = 1) { walletServicesStarter.start() }
    }

    @Test
    fun start_firstUnlock_startsTonConnectOnce() = runTest {
        startSession()

        vaultState.value = unlocked()
        vaultState.value = DesktopVaultState.Locked
        vaultState.value = unlocked()

        verify(exactly = 1) { tonConnectManager.start() }
    }

    @Test
    fun awaitReleased_servicesStarted_closesTonConnectOfTheDroppedAccounts() = runTest {
        val session = startSession()
        vaultState.value = unlocked()

        session.awaitReleased(setOf(KEPT_ACCOUNT))

        coVerify(exactly = 1) { tonConnectManager.retainAccounts(setOf(KEPT_ACCOUNT)) }
    }

    @Test
    fun onSessionClosing_tonConnectBuilt_closesEveryKit() = runTest {
        val session = startSession()
        vaultState.value = unlocked()

        session.onSessionClosing()

        coVerify(exactly = 1) { tonConnectManager.retainAccounts(emptySet()) }
    }

    @Test
    fun onSessionClosing_beforeAnyUnlock_neverBuildsTonConnect() = runTest {
        val session = startSession()

        session.onSessionClosing()

        assertFalse(lazyTonConnectManager.isInitialized())
    }

    @Test
    fun start_noUnlock_neverStartsTheWalletServices() = runTest {
        startSession()

        vaultState.value = DesktopVaultState.NoVault

        coVerify(exactly = 0) { walletServicesStarter.start() }
    }

    @Test
    fun start_noVaultBeforeAnyUnlock_neverBuildsThePremiumUseCase() = runTest {
        startSession()

        vaultState.value = DesktopVaultState.NoVault

        verify { accountManager.unload() }
        assertFalse(lazyCheckPremiumUseCase.isInitialized())
    }

    @Test
    fun start_wipe_stopsWarmUpUnloadsAccountsAndResetsLevelAndTerms() = runTest {
        startSession()
        vaultState.value = unlocked()

        vaultState.value = DesktopVaultState.NoVault

        coVerifyOrder {
            checkPremiumUseCase.stopWarmUp()
            accountManager.unload()
            userManager.resetUserLevel()
            termsManager.reload()
        }
        verify { contactsRepository.clear() }
    }

    @Test
    fun start_unlockRacedByAWipe_keepsFollowingTheVault() = runTest {
        every { userManager.setUserLevel(0) } throws StorageLockedException()
        startSession()

        vaultState.value = unlocked()
        vaultState.value = DesktopVaultState.NoVault

        verify { accountManager.unload() }
    }

    @Test
    fun log_whileALevelReleaseRuns_isDroppedAndWorksAfterIt() = runTest {
        val release = CompletableDeferred<Unit>()
        coEvery { adapterManager.awaitReleased(any()) } coAnswers { release.await() }
        val session = startSession()
        vaultState.value = unlocked()
        session.onLevelNarrowing()
        val running = launch(UnconfinedTestDispatcher(testScheduler)) { session.awaitReleased(emptySet()) }

        logs.insert(LOG_ENTRY)
        verify(exactly = 0) { logsDao.insert(any()) }

        release.complete(Unit)
        running.join()
        logs.insert(LOG_ENTRY)
        verify(exactly = 1) { logsDao.insert(LOG_ENTRY) }
    }

    @Test
    fun log_callMadeDuringARelease_isDroppedEvenIfItRunsAfterTheRelease() {
        logGate.releaseStarted()
        val queuedDuringTheRelease = GatedLogsDao(logsDao, logGate)

        logGate.releaseEnded()
        queuedDuringTheRelease.insert(LOG_ENTRY)

        verify(exactly = 0) { logsDao.insert(any()) }
    }

    @Test
    fun log_levelReleaseCancelledByAColdSwitch_worksAgain() = runTest {
        coEvery { adapterManager.awaitReleased(any()) } coAnswers { awaitCancellation() }
        val session = startSession()
        vaultState.value = unlocked()
        session.onLevelNarrowing()
        val running = launch(UnconfinedTestDispatcher(testScheduler)) { session.awaitReleased(emptySet()) }
        logs.insert(LOG_ENTRY)
        verify(exactly = 0) { logsDao.insert(any()) }

        running.cancelAndJoin()
        session.onSessionClosing()

        logs.insert(LOG_ENTRY)
        verify(exactly = 1) { logsDao.insert(LOG_ENTRY) }
    }

    @Test
    fun log_releaseBeforeTheWalletServicesStarted_worksAgainAtOnce() = runTest {
        val session = startSession()
        session.onLevelNarrowing()

        session.awaitReleased(emptySet())

        logs.insert(LOG_ENTRY)
        verify(exactly = 1) { logsDao.insert(LOG_ENTRY) }
    }

    @Test
    fun log_narrowingFailedBeforeAnyRelease_worksAgainOnceTheSessionCloses() = runTest {
        val session = startSession()
        session.onLevelNarrowing()
        logs.insert(LOG_ENTRY)
        verify(exactly = 0) { logsDao.insert(any()) }

        session.onSessionClosing()

        logs.insert(LOG_ENTRY)
        verify(exactly = 1) { logsDao.insert(LOG_ENTRY) }
    }

    private fun TestScope.startSession(): DesktopWalletSession {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        return DesktopWalletSession(
            vault = vault,
            userManager = userManager,
            accountManager = accountManager,
            termsManager = termsManager,
            contactsRepository = contactsRepository,
            checkPremiumUseCase = lazyCheckPremiumUseCase,
            walletServicesStarter = lazy { walletServicesStarter },
            accountHolders = DesktopWalletAccountHolders(
                adapterManager = lazyAdapterManager,
                transactionAdapterManager = lazy { mockk<TransactionAdapterManager>(relaxed = true) },
                offlineModeManager = lazy { offlineModeManager },
                bitcoinKitDatabaseManager = lazy { mockk<BitcoinKitDatabaseManager>(relaxed = true) },
                amlStatusManager = lazy { mockk<AmlStatusManager>(relaxed = true) },
                tonConnectManager = lazyTonConnectManager,
                logGate = logGate,
            ),
            dispatcherProvider = TestDispatcherProvider(dispatcher, backgroundScope),
        ).also { it.start(backgroundScope) }
    }

    private companion object {
        const val KEPT_ACCOUNT = "kept-account"
        val LOG_ENTRY = LogEntry(date = 1, level = 4, actionId = "upper-account", message = "stopping")
    }

    private fun unlocked() = DesktopVaultState.Unlocked(mockk(relaxed = true))
}
