package cash.p.terminal.core.managers

import cash.p.terminal.modules.pin.core.PinDbStorage
import io.horizontalsystems.core.ISmsNotificationSettings

class AndroidPlatformAccountDataCleaner(
    private val smsNotificationSettings: ISmsNotificationSettings,
    private val pinDbStorage: PinDbStorage,
) : PlatformAccountDataCleaner {

    override fun clear(accountId: String) {
        pinDbStorage.getAllLevels().forEach { level ->
            if (smsNotificationSettings.getSmsNotificationAccountId(level) == accountId) {
                smsNotificationSettings.setSmsNotificationAccountId(level, null)
                smsNotificationSettings.setSmsNotificationAddress(level, null)
                smsNotificationSettings.setSmsNotificationMemo(level, null)
            }
        }
    }
}
