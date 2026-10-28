package cash.p.terminal.modules.paycore

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AndroidPayCorePreferencesTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val legacyPrefs = context.getSharedPreferences("paycore_secure_prefs", Context.MODE_PRIVATE)
    private val preferences = AndroidPayCorePreferences(context)

    @Test
    fun getString_storedByOlderBuild_readFromTheSameFile() {
        legacyPrefs.edit { putString("phone_0", "encrypted") }

        assertEquals("encrypted", preferences.getString("phone_0"))
    }

    @Test
    fun putStringDurably_value_storedInTheSameFile() {
        preferences.putStringDurably("verification_status_0", "encrypted")

        assertEquals("encrypted", legacyPrefs.getString("verification_status_0", null))
    }
}
