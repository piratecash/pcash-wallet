package cash.p.terminal.modules.settings.security.autolock

import cash.p.terminal.resources.Res
import cash.p.terminal.resources.SettingsSecurity_AutoLock_After15Min
import cash.p.terminal.resources.SettingsSecurity_AutoLock_After1Hour
import cash.p.terminal.resources.SettingsSecurity_AutoLock_After1Min
import cash.p.terminal.resources.SettingsSecurity_AutoLock_After2Min
import cash.p.terminal.resources.SettingsSecurity_AutoLock_After30Min
import cash.p.terminal.resources.SettingsSecurity_AutoLock_After3Min
import cash.p.terminal.resources.SettingsSecurity_AutoLock_After4Min
import cash.p.terminal.resources.SettingsSecurity_AutoLock_After5Min
import cash.p.terminal.resources.SettingsSecurity_AutoLock_Immediate
import org.jetbrains.compose.resources.StringResource

enum class AutoLockInterval(val raw: String, val title: StringResource) {
    IMMEDIATE("immediate", Res.string.SettingsSecurity_AutoLock_Immediate),
    AFTER_1_MIN("1min", Res.string.SettingsSecurity_AutoLock_After1Min),
    AFTER_2_MIN("2min", Res.string.SettingsSecurity_AutoLock_After2Min),
    AFTER_3_MIN("3min", Res.string.SettingsSecurity_AutoLock_After3Min),
    AFTER_4_MIN("4min", Res.string.SettingsSecurity_AutoLock_After4Min),
    AFTER_5_MIN("5min", Res.string.SettingsSecurity_AutoLock_After5Min),
    AFTER_15_MIN("15min", Res.string.SettingsSecurity_AutoLock_After15Min),
    AFTER_30_MIN("30min", Res.string.SettingsSecurity_AutoLock_After30Min),
    AFTER_1_HOUR("1hour", Res.string.SettingsSecurity_AutoLock_After1Hour);

    val intervalInSeconds: Int
        get() = when (this) {
            IMMEDIATE -> 0
            AFTER_1_MIN -> 60
            AFTER_2_MIN -> 2 * 60
            AFTER_3_MIN -> 3 * 60
            AFTER_4_MIN -> 4 * 60
            AFTER_5_MIN -> 5 * 60
            AFTER_15_MIN -> 15 * 60
            AFTER_30_MIN -> 30 * 60
            AFTER_1_HOUR -> 60 * 60
        }

    companion object {
        fun fromRaw(raw: String): AutoLockInterval? {
            return entries.find { it.raw == raw }
        }
    }
}
