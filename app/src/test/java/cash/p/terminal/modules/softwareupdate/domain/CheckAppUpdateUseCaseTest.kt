package cash.p.terminal.modules.softwareupdate.domain

import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.managers.LanguageManager
import cash.p.terminal.network.github.domain.entity.AppRelease
import cash.p.terminal.network.github.domain.repository.AppUpdateRepository
import io.horizontalsystems.core.ISystemInfoManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class CheckAppUpdateUseCaseTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val repository = mockk<AppUpdateRepository>()
    private val systemInfoManager = mockk<ISystemInfoManager>()
    private val localStorage = mockk<ILocalStorage>(relaxed = true)
    private val languageManager = mockk<LanguageManager>()
    private val timeProvider = mockk<TimeProvider>()

    private lateinit var useCase: CheckAppUpdateUseCase

    @Before
    fun setup() {
        every { languageManager.currentLanguage } returns "en"
        every { timeProvider.now() } returns CHECK_TIME
        useCase = CheckAppUpdateUseCase(
            repository = repository,
            systemInfoManager = systemInfoManager,
            localStorage = localStorage,
            languageManager = languageManager,
            dispatcherProvider = TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher)),
            timeProvider = timeProvider,
        )
    }

    @Test
    fun invoke_newerVersion_returnsTaggedChangelogAndPersists() = runTest(dispatcher) {
        every { systemInfoManager.appVersion } returns "0.57.0"
        val latest = release("0.58.0", "0.58")
        coEvery { repository.getLatestRelease() } returns latest
        coEvery {
            repository.getChangelogMarkdown(
                minor = latest.minor,
                isActiveBranch = true,
                tagName = latest.tagName,
                language = "en",
            )
        } returns """
            ## 🚀 Version 0.58.0 Update
            ### Improvements
            - Improved update details
            ### Fixes
            - Fixed changelog version
        """.trimIndent()

        val result = useCase()

        assertEquals(UpdateStatus.Available(latest, ChangelogSnippet(improvements = 1, fixes = 1)), result)
        coVerify(exactly = 1) {
            repository.getChangelogMarkdown(
                minor = latest.minor,
                isActiveBranch = true,
                tagName = latest.tagName,
                language = "en",
            )
        }
        verify { localStorage.latestKnownVersion = "0.58.0" }
        verify { localStorage.lastUpdateCheckTimestamp = CHECK_TIME }
    }

    @Test
    fun invoke_sameVersion_returnsUpToDateWithRelease() = runTest(dispatcher) {
        every { systemInfoManager.appVersion } returns "0.58.0"
        val latest = release("0.58.0", "0.58")
        coEvery { repository.getLatestRelease() } returns latest

        assertEquals(UpdateStatus.UpToDate(latest), useCase())
        coVerify(exactly = 0) { repository.getChangelogMarkdown(any(), any(), any(), any()) }
        verify { localStorage.lastUpdateCheckTimestamp = CHECK_TIME }
    }

    @Test
    fun invoke_olderLatestVersion_returnsUpToDateWithoutRelease() = runTest(dispatcher) {
        every { systemInfoManager.appVersion } returns "0.58.0"
        coEvery { repository.getLatestRelease() } returns release("0.57.2", "0.57")

        assertEquals(UpdateStatus.UpToDate(release = null), useCase())
        coVerify(exactly = 0) { repository.getChangelogMarkdown(any(), any(), any(), any()) }
        verify { localStorage.lastUpdateCheckTimestamp = CHECK_TIME }
    }

    @Test
    fun invoke_repositoryFails_returnsErrorButStillPersistsTimestamp() = runTest(dispatcher) {
        every { systemInfoManager.appVersion } returns "0.58.0"
        coEvery { repository.getLatestRelease() } throws RuntimeException("network")

        assertEquals(UpdateStatus.Error, useCase())
        verify { localStorage.lastUpdateCheckTimestamp = CHECK_TIME }
    }

    private fun release(version: String, minor: String) = AppRelease(
        version = version,
        minor = minor,
        tagName = "v$version-fdroid",
        publishedAt = Instant.EPOCH,
        htmlUrl = "https://example",
        apkSizeBytes = 1L,
        apkDownloadUrl = "https://example.apk",
    )

    private companion object {
        const val CHECK_TIME = 42_000L
    }
}
