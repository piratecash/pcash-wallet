package io.horizontalsystems.core

interface ISystemInfoManager {
    val appVersion: String
    val appVersionFull: String
    val appVersionDisplay: String
    val isDeviceSecure: Boolean
    val isSystemLockOff: Boolean
    val biometricAuthSupported: Boolean
    val deviceModel: String
    val osVersion: String
    fun getSigningCertFingerprint(): String?
}
