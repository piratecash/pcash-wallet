package cash.p.terminal.modules.softwareupdate

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import cash.p.terminal.modules.softwareupdate.domain.ChangelogSnippet
import cash.p.terminal.modules.softwareupdate.domain.InstallSource
import cash.p.terminal.modules.softwareupdate.domain.UpdateCheckInterval
import cash.p.terminal.modules.softwareupdate.domain.UpdateStatus
import cash.p.terminal.network.github.domain.entity.AppRelease
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "en")
class SoftwareUpdateScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun screen_googlePlayNotAvailable_hidesUpdateCardAndUnverifiedDetails() {
        setContent(
            status = UpdateStatus.UpToDate(release = null),
            installSource = InstallSource.GOOGLE_PLAY,
        )

        composeTestRule.onNodeWithText("Update now").assertDoesNotExist()
        composeTestRule.onNodeWithText("New update available").assertDoesNotExist()
        composeTestRule.onNodeWithText("Details").assertDoesNotExist()
    }

    @Test
    fun screen_googlePlayAvailable_showsGenericCardWithoutGithubMetadata() {
        setContent(
            status = UpdateStatus.Available(
                release = null,
                changelogSnippet = null,
                availableVersionCode = 257,
            ),
            installSource = InstallSource.GOOGLE_PLAY,
        )

        composeTestRule.onNodeWithText("Update now").assertExists()
        composeTestRule.onNodeWithText("Source: Play Market").assertExists()
        composeTestRule.onNodeWithText("Version 0.60.0").assertDoesNotExist()
        composeTestRule.onNodeWithText("Details…").assertDoesNotExist()
    }

    @Test
    fun screen_otherAvailable_preservesGithubMetadataAndDestinationUi() {
        setContent(
            status = UpdateStatus.Available(
                release = release(),
                changelogSnippet = ChangelogSnippet(improvements = 2, fixes = 1),
            ),
            installSource = InstallSource.OTHER,
        )

        composeTestRule.onNodeWithText("Version 0.60.0").assertExists()
        composeTestRule.onNodeWithText("This update: 2 improvements, 1 fixes").assertExists()
        composeTestRule.onNodeWithText("Details…").assertExists()
        composeTestRule.onNodeWithText("Source: GitHub").assertExists()
    }

    private fun setContent(status: UpdateStatus, installSource: InstallSource) {
        composeTestRule.setContent {
            ComposeAppTheme {
                SoftwareUpdateScreen(
                    uiState = SoftwareUpdateUiState(
                        currentVersion = "0.59.4",
                        interval = UpdateCheckInterval.DAY,
                        lastCheckTimestamp = null,
                        updateStatus = status,
                        installSource = installSource,
                    ),
                    onBack = {},
                    onIntervalChange = {},
                    onRetry = {},
                    onHistoryClick = {},
                    onDetailsClick = {},
                    onUpdateNowClick = {},
                )
            }
        }
    }

    private fun release() = AppRelease(
        version = "0.60.0",
        minor = "0.60",
        tagName = "v0.60.0-google",
        publishedAt = Instant.EPOCH,
        htmlUrl = "https://example.com/release",
        apkSizeBytes = 1L,
        apkDownloadUrl = "https://example.com/app.apk",
    )
}
