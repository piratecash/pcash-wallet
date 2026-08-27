package cash.p.terminal.network.github.domain.repository

import cash.p.terminal.network.github.domain.entity.AppRelease

interface AppUpdateRepository {
    suspend fun getLatestRelease(): AppRelease

    /**
     * Minor version branches that have an archived changelog (e.g. "0.57", "0.56", …), newest first,
     * derived from the release-notes folder so the history list matches the changelog files 1:1.
     */
    suspend fun getVersionHistoryMinors(): List<String>

    /**
     * Localized changelog markdown for a minor branch (e.g. minor = "0.57").
     * [isActiveBranch] uses the root changelog_{lang}.md at [tagName], when provided; otherwise
     * it uses the archived release-notes/{lang}/{minor}.x.md. Both paths fall back to English.
     */
    suspend fun getChangelogMarkdown(
        minor: String,
        isActiveBranch: Boolean,
        tagName: String?,
        language: String,
    ): String?
}
