package cash.p.terminal.core

interface ReleaseNotesStorage {
    var changelogShownForAppVersion: String?
    var showChangelogAfterUpdate: Boolean
}
