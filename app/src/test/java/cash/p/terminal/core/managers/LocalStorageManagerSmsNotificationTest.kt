package cash.p.terminal.core.managers

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LocalStorageManagerSmsNotificationTest {

    private val storage = LocalStorageManager(
        ApplicationProvider.getApplicationContext<Application>()
            .getSharedPreferences("local_storage_sms_test", Context.MODE_PRIVATE)
    )

    @Test
    fun getSmsNotificationEnabled_neverToggledWithSavedConfig_true() {
        saveConfig()

        assertTrue(storage.getSmsNotificationEnabled(LEVEL))
    }

    @Test
    fun getSmsNotificationEnabled_neverToggledNoConfig_false() {
        assertFalse(storage.getSmsNotificationEnabled(LEVEL))
    }

    @Test
    fun getSmsNotificationEnabled_toggledOffWithSavedConfig_false() {
        saveConfig()
        storage.setSmsNotificationEnabled(LEVEL, false)

        assertFalse(storage.getSmsNotificationEnabled(LEVEL))
    }

    @Test
    fun getSmsNotificationEnabled_toggledOnOtherLevel_doesNotAffectLevel() {
        storage.setSmsNotificationEnabled(LEVEL + 1, true)

        assertFalse(storage.getSmsNotificationEnabled(LEVEL))
    }

    private fun saveConfig() {
        storage.setSmsNotificationAccountId(LEVEL, "account-1")
        storage.setSmsNotificationAddress(LEVEL, "zs1recipient")
    }

    private companion object {
        const val LEVEL = 0
    }
}
