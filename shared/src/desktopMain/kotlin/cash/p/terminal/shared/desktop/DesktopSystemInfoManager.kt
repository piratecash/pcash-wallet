package cash.p.terminal.shared.desktop

import cash.p.terminal.network.data.AppHeadersProvider
import io.horizontalsystems.core.ISystemInfoManager

/** Desktop has no OS lock, biometrics or APK signature: those report absent. */
internal class DesktopSystemInfoManager(appHeadersProvider: AppHeadersProvider) : ISystemInfoManager {
    override val appVersion: String = appHeadersProvider.appVersion
    override val appVersionFull: String = appVersion
    override val appVersionDisplay: String = appVersion
    override val isDeviceSecure: Boolean = false
    override val isSystemLockOff: Boolean = false
    override val biometricAuthSupported: Boolean = false
    override val deviceModel: String = "Desktop"
    override val osVersion: String = "${System.getProperty("os.name")} ${System.getProperty("os.version")}"

    override fun getSigningCertFingerprint(): String? = null
}
