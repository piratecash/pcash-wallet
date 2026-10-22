package cash.p.terminal.core.managers

import cash.p.terminal.modules.pin.core.PinDbStorage
import io.horizontalsystems.core.ISmsNotificationSettings
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class AndroidPlatformAccountDataCleanerTest {

    private val smsNotificationSettings = mockk<ISmsNotificationSettings>(relaxed = true)
    private val pinDbStorage = mockk<PinDbStorage>(relaxed = true)
    private val cleaner = AndroidPlatformAccountDataCleaner(smsNotificationSettings, pinDbStorage)

    @Test
    fun clear_smsNotificationConfigured_clearsSmsSettings() {
        val accountId = "acc-sms"

        every { pinDbStorage.getAllLevels() } returns listOf(0, 1, 2)
        every { smsNotificationSettings.getSmsNotificationAccountId(0) } returns "other-account"
        every { smsNotificationSettings.getSmsNotificationAccountId(1) } returns accountId
        every { smsNotificationSettings.getSmsNotificationAccountId(2) } returns null

        cleaner.clear(accountId)

        // Should only clear level 1 where accountId matches
        verify(exactly = 0) { smsNotificationSettings.setSmsNotificationAccountId(0, null) }
        verify(exactly = 1) { smsNotificationSettings.setSmsNotificationAccountId(1, null) }
        verify(exactly = 1) { smsNotificationSettings.setSmsNotificationAddress(1, null) }
        verify(exactly = 1) { smsNotificationSettings.setSmsNotificationMemo(1, null) }
        verify(exactly = 0) { smsNotificationSettings.setSmsNotificationAccountId(2, null) }
    }

    @Test
    fun clear_noSmsNotification_doesNotClearSettings() {
        val accountId = "acc-no-sms"

        every { pinDbStorage.getAllLevels() } returns listOf(0, 1)
        every { smsNotificationSettings.getSmsNotificationAccountId(any()) } returns "other-account"

        cleaner.clear(accountId)

        verify(exactly = 0) { smsNotificationSettings.setSmsNotificationAccountId(any(), any()) }
        verify(exactly = 0) { smsNotificationSettings.setSmsNotificationAddress(any(), any()) }
        verify(exactly = 0) { smsNotificationSettings.setSmsNotificationMemo(any(), any()) }
    }
}
