package cash.p.terminal.core.managers

import cash.p.terminal.core.AppVersionStorage
import cash.p.terminal.entities.AppVersion
import io.horizontalsystems.core.ISystemInfoManager
import java.util.Date

class AppVersionManager(
    private val systemInfoManager: ISystemInfoManager,
    private val storage: AppVersionStorage
) {

    fun storeAppVersion() {
        val versions = storage.appVersions.toMutableList()
        val lastVersion = versions.lastOrNull()

        if (lastVersion == null || lastVersion.version != systemInfoManager.appVersion) {
            versions.add(AppVersion(systemInfoManager.appVersion, Date().time))
            storage.appVersions = versions
        }
    }

}
