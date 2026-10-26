package cash.p.terminal.shared.desktop.session

import cash.p.terminal.modules.contacts.ContactsRepository
import cash.p.terminal.core.ITermsManager
import cash.p.terminal.core.StorageLockedException
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.managers.WalletServicesStarter
import cash.p.terminal.premium.domain.usecase.CheckPremiumUseCase
import cash.p.terminal.shared.desktop.DesktopWalletSession
import cash.p.terminal.shared.desktop.vault.DesktopVault
import cash.p.terminal.shared.desktop.vault.DesktopVaultState
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.managers.UserManager
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    private val lazyCheckPremiumUseCase = lazy { checkPremiumUseCase }

    @Test
    fun start_everyUnlock_setsUserLevelZeroAndStartsThePremiumWarmUp() = runTest {
        startSession()

        vaultState.value = unlocked()
        vaultState.value = DesktopVaultState.Locked
        vaultState.value = unlocked()

        verify(exactly = 2) { userManager.setUserLevel(0) }
        verify(exactly = 2) { checkPremiumUseCase.startWarmUp() }
        coVerify(exactly = 0) { checkPremiumUseCase.stopWarmUp() }
        verify(exactly = 0) { accountManager.unload() }
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

    private fun TestScope.startSession() {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        DesktopWalletSession(
            vault = vault,
            userManager = userManager,
            accountManager = accountManager,
            termsManager = termsManager,
            contactsRepository = contactsRepository,
            checkPremiumUseCase = lazyCheckPremiumUseCase,
            walletServicesStarter = lazy { walletServicesStarter },
            dispatcherProvider = TestDispatcherProvider(dispatcher, backgroundScope),
        ).start(backgroundScope)
    }

    private fun unlocked() = DesktopVaultState.Unlocked(mockk(relaxed = true))
}
