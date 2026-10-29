package cash.p.terminal.modules.settings.appstatus

/** App Info rows that only the platform can fill. */
interface AppStatusPlatformInfo {
    fun buildInfo(): Map<String, String>
    fun deviceInfo(): Map<String, String>

    /** `null` hides the Unique Code row. */
    fun uniqueCode(): String?
}
