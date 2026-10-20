package cash.p.terminal.shared.desktop.session

import cash.p.terminal.core.ITermsManager
import cash.p.terminal.core.StorageLockedException
import cash.p.terminal.core.TestDispatcherProvider
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

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopWalletSessionTest {

    private val vaultState = MutableStateFlow<DesktopVaultState>(DesktopVaultState.Locked)
    private val vault = mockk<DesktopVault> { every { state } returns vaultState }
    private val userManager = mockk<UserManager>(relaxed = true)
    private val accountManager = mockk<IAccountManager>(relaxed = true)
    private val termsManager = mockk<ITermsManager>(relaxed = true)
    private val checkPremiumUseCase = mockk<CheckPremiumUseCase>(relaxed = true)

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
            checkPremiumUseCase = checkPremiumUseCase,
            dispatcherProvider = TestDispatcherProvider(dispatcher, backgroundScope),
        ).start(backgroundScope)
    }

    private fun unlocked() = DesktopVaultState.Unlocked(mockk(relaxed = true))
}
