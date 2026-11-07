package cash.p.terminal.shared.desktop

import cash.p.terminal.modules.blockchainstatus.StatusReportBuildInfo
import cash.p.terminal.modules.settings.appstatus.AppStatusPlatformInfo

internal class DesktopAppStatusPlatformInfo(private val buildInfo: StatusReportBuildInfo) : AppStatusPlatformInfo {
    override fun buildInfo(): Map<String, String> = buildInfo.entries()

    override fun deviceInfo(): Map<String, String> = linkedMapOf(
        "JVM Heap Limit" to "${Runtime.getRuntime().maxMemory() / BYTES_IN_MB}MB",
        "CPU Cores" to Runtime.getRuntime().availableProcessors().toString(),
    )

    override fun uniqueCode(): String? = null

    private companion object {
        const val BYTES_IN_MB = 1024 * 1024
    }
}
