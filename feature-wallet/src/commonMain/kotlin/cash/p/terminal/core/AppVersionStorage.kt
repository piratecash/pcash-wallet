package cash.p.terminal.core

import cash.p.terminal.entities.AppVersion

interface AppVersionStorage {
    var appVersions: List<AppVersion>
}
