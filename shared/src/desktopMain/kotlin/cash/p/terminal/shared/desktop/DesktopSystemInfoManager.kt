package cash.p.terminal.shared.desktop

import io.horizontalsystems.core.ISystemInfoManager

/** Desktop has no OS lock, biometrics or APK signature: those report absent. */
internal class DesktopSystemInfoManager(version: DesktopAppVersion) : ISystemInfoManager {
    override val appVersion: String = version.name
    override val appVersionFull: String = appVersion
    override val appVersionDisplay: String = appVersion
    override val isDeviceSecure: Boolean = false
    override val isSystemLockOff: Boolean = false
    override val biometricAuthSupported: Boolean = false
    override val deviceModel: String = "Desktop"
    override val osVersion: String = "${System.getProperty("os.name")} ${System.getProperty("os.version")}"

    override fun getSigningCertFingerprint(): String? = null
}
