package cash.p.terminal.network.github

import cash.p.terminal.network.github.api.GithubApi
import cash.p.terminal.network.github.data.entity.GithubContentDto
import cash.p.terminal.network.github.data.mapper.GithubReleaseMapper
import cash.p.terminal.network.github.data.repository.AppUpdateRepositoryImpl
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class AppUpdateRepositoryImplTest {

    private val api = mockk<GithubApi>()
    private val repository = AppUpdateRepositoryImpl(api, GithubReleaseMapper())

    @After
    fun tearDown() = unmockkAll()

    @Test
    fun getVersionHistoryMinors_parsesSortsAndDropsNonMinorFiles() = runTest {
        coEvery { api.getFolderContents("release-notes/en") } returns listOf(
            GithubContentDto("0.55.x.md"),
            GithubContentDto("0.57.x.md"),
            GithubContentDto("README.md"),
            GithubContentDto("0.56.x.md"),
            GithubContentDto("0.9.x.md"),
        )

        assertEquals(listOf("0.57", "0.56", "0.55", "0.9"), repository.getVersionHistoryMinors())
    }

    @Test
    fun getChangelogMarkdown_activeBranchWithTag_usesTaggedRootChangelog() = runTest {
        coEvery { api.getRawFile("changelog_en.md", RELEASE_TAG) } returns "root en"

        assertEquals(
            "root en",
            getChangelog(minor = "0.58", isActiveBranch = true, language = "en", tagName = RELEASE_TAG),
        )
    }

    @Test
    fun getChangelogMarkdown_taggedLocalizedFileMissing_fallsBackToEnglishAtSameTag() = runTest {
        coEvery { api.getRawFile("changelog_ru.md", RELEASE_TAG) } returns null
        coEvery { api.getRawFile("changelog_en.md", RELEASE_TAG) } returns "root en"

        assertEquals(
            "root en",
            getChangelog(minor = "0.58", isActiveBranch = true, language = "ru", tagName = RELEASE_TAG),
        )
    }

    @Test
    fun getChangelogMarkdown_activeBranchWithoutTag_usesMasterRootChangelog() = runTest {
        coEvery { api.getRawFile("changelog_en.md", null) } returns "root en"

        assertEquals(
            "root en",
            getChangelog(minor = "0.58", isActiveBranch = true, language = "en"),
        )
    }

    @Test
    fun getChangelogMarkdown_archivedMinor_usesReleaseNotesFile() = runTest {
        coEvery { api.getRawFile("release-notes/ru/0.57.x.md", null) } returns "archived ru"

        assertEquals(
            "archived ru",
            getChangelog(minor = "0.57", isActiveBranch = false, language = "ru", tagName = RELEASE_TAG),
        )
    }

    @Test
    fun getChangelogMarkdown_archivedFileMissingInLanguage_fallsBackToEnglishArchived() = runTest {
        coEvery { api.getRawFile("release-notes/ru/0.57.x.md", null) } returns null
        coEvery { api.getRawFile("release-notes/en/0.57.x.md", null) } returns "archived en"

        assertEquals(
            "archived en",
            getChangelog(minor = "0.57", isActiveBranch = false, language = "ru"),
        )
    }

    @Test
    fun getChangelogMarkdown_unsupportedLanguage_fallsBackToEnglish() = runTest {
        coEvery { api.getRawFile("release-notes/en/0.57.x.md", null) } returns "archived en"

        assertEquals(
            "archived en",
            getChangelog(minor = "0.57", isActiveBranch = false, language = "de"),
        )
    }

    private suspend fun getChangelog(
        minor: String,
        isActiveBranch: Boolean,
        language: String,
        tagName: String? = null,
    ) = repository.getChangelogMarkdown(minor, isActiveBranch, tagName, language)

    private companion object {
        const val RELEASE_TAG = "v0.58.0-fdroid"
    }
}
