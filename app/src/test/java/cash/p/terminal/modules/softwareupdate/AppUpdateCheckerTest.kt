package cash.p.terminal.modules.softwareupdate

import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.modules.softwareupdate.domain.CheckAppUpdateUseCase
import cash.p.terminal.modules.softwareupdate.domain.InstallSource
import cash.p.terminal.modules.softwareupdate.domain.InstallSourceProvider
import cash.p.terminal.modules.softwareupdate.domain.ShouldAutoCheckUseCase
import cash.p.terminal.modules.softwareupdate.domain.UpdateStatus
import io.horizontalsystems.core.ISystemInfoManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppUpdateCheckerTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val checkAppUpdateUseCase = mockk<CheckAppUpdateUseCase>()
    private val shouldAutoCheckUseCase = mockk<ShouldAutoCheckUseCase>(relaxed = true)
    private val systemInfoManager = mockk<ISystemInfoManager>()
    private val localStorage = mockk<ILocalStorage>(relaxed = true)
    private val installSourceProvider = mockk<InstallSourceProvider>()

    @Test
    fun updateAvailable_googlePlayWithNewerGithubCache_startsFalse() {
        every { installSourceProvider.installSource } returns InstallSource.GOOGLE_PLAY
        every { localStorage.latestKnownVersion } returns "0.60.0"
        every { systemInfoManager.appVersion } returns "0.59.4"

        assertFalse(createChecker().updateAvailable.value)
    }

    @Test
    fun checkNow_googlePlayAvailableThenError_clearsBadgeAfterError() = runTest(dispatcher) {
        every { installSourceProvider.installSource } returns InstallSource.GOOGLE_PLAY
        coEvery { checkAppUpdateUseCase() } returnsMany listOf(
            UpdateStatus.Available(
                release = null,
                changelogSnippet = null,
                availableVersionCode = 257,
            ),
            UpdateStatus.Error,
        )
        val checker = createChecker()

        checker.checkNow()
        assertTrue(checker.updateAvailable.value)

        checker.checkNow()
        assertFalse(checker.updateAvailable.value)
    }

    @Test
    fun checkNow_otherInstallError_preservesGithubCacheBadge() = runTest(dispatcher) {
        every { installSourceProvider.installSource } returns InstallSource.OTHER
        every { localStorage.latestKnownVersion } returns "0.60.0"
        every { systemInfoManager.appVersion } returns "0.59.4"
        coEvery { checkAppUpdateUseCase() } returns UpdateStatus.Error
        val checker = createChecker()

        checker.checkNow()

        assertTrue(checker.updateAvailable.value)
    }

    private fun createChecker() = AppUpdateChecker(
        checkAppUpdateUseCase = checkAppUpdateUseCase,
        shouldAutoCheckUseCase = shouldAutoCheckUseCase,
        systemInfoManager = systemInfoManager,
        localStorage = localStorage,
        dispatcherProvider = TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher)),
        installSourceProvider = installSourceProvider,
    )
}
