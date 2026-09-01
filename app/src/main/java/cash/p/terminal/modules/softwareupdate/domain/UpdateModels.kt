package cash.p.terminal.modules.softwareupdate.domain

import cash.p.terminal.network.github.domain.entity.AppRelease

private const val DAY_MILLIS = 24L * 60 * 60 * 1000

enum class UpdateCheckInterval(val millis: Long) {
    DAY(DAY_MILLIS),
    WEEK(7 * DAY_MILLIS),
    MONTH(30 * DAY_MILLIS);
}

enum class InstallSource { GOOGLE_PLAY, FDROID, OTHER }

data class ChangelogSnippet(val improvements: Int, val fixes: Int)

fun interface GooglePlayUpdateAvailabilityProvider {
    suspend fun getAvailability(): GooglePlayUpdateAvailability
}

sealed interface GooglePlayUpdateAvailability {
    data object NotAvailable : GooglePlayUpdateAvailability
    data class Available(val versionCode: Int) : GooglePlayUpdateAvailability
    data class DeveloperTriggeredUpdateInProgress(val versionCode: Int) : GooglePlayUpdateAvailability
    data object Error : GooglePlayUpdateAvailability
}

sealed interface UpdateStatus {
    data object Unknown : UpdateStatus
    data class UpToDate(val release: AppRelease?) : UpdateStatus
    data class Available(
        val release: AppRelease?,
        val changelogSnippet: ChangelogSnippet?,
        val availableVersionCode: Int? = null,
    ) : UpdateStatus

    data object Error : UpdateStatus
}
