package cash.p.terminal.modules.settings.appstatus

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.providers.AppConfigProvider
import cash.p.terminal.feature.miniapp.domain.storage.IUniqueCodeStorage
import cash.p.terminal.modules.blockchainstatus.reportBuildInfo
import io.horizontalsystems.core.ISystemInfoManager

class AndroidAppStatusPlatformInfo(
    private val context: Context,
    private val systemInfoManager: ISystemInfoManager,
    private val localStorage: ILocalStorage,
    private val uniqueCodeStorage: IUniqueCodeStorage,
) : AppStatusPlatformInfo {

    override fun buildInfo(): Map<String, String> = reportBuildInfo(
        systemInfoManager.appVersionFull,
        AppConfigProvider.appGitBranch,
    )

    override fun deviceInfo(): Map<String, String> {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = am.memoryClass
        val largeMemory = am.largeMemoryClass
        val isLowRam = am.isLowRamDevice
        val perf = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.VERSION.MEDIA_PERFORMANCE_CLASS else null

        val classLevel = when {
            perf == null -> "Unknown"
            isLowRam || memory < 128 -> "Low"
            perf >= 33 || memory >= 256 -> "High"
            else -> "Medium"
        }
        return linkedMapOf(
            "App Heap Limit" to "${memory}MB (${largeMemory}MB)",
            "Performance Class" to "$classLevel ($perf)",
            "System pin required" to if (localStorage.isSystemPinRequired) "Yes" else "No",
        )
    }

    override fun uniqueCode(): String {
        val code = uniqueCodeStorage.uniqueCode
        if (code.isBlank()) return "None"

        val edge = if (code.length > UNIQUE_CODE_EDGE_CHARS * 2) {
            UNIQUE_CODE_EDGE_CHARS
        } else {
            UNIQUE_CODE_SHORT_EDGE_CHARS
        }
        return if (code.length <= edge * 2) code else "${code.take(edge)}...${code.takeLast(edge)}"
    }

    private companion object {
        const val UNIQUE_CODE_EDGE_CHARS = 10
        const val UNIQUE_CODE_SHORT_EDGE_CHARS = 3
    }
}
