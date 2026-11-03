package cash.p.terminal.modules.multiswap.providers

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
class AndroidSwapProvidersStorageTest {

    private val preferences = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("default", Context.MODE_PRIVATE)
    private val storage = AndroidSwapProvidersStorage(preferences)

    @Test
    fun disabledProviderIds_storedByOlderBuild_readFromTheSameKey() {
        preferences.edit { putStringSet("disabled_swap_providers", setOf("oneinch")) }

        assertEquals(setOf("oneinch"), storage.disabledProviderIds)
    }

    @Test
    fun disabledProviderIds_written_storedUnderTheSameKey() {
        storage.disabledProviderIds = setOf("changenow")

        assertEquals(setOf("changenow"), preferences.getStringSet("disabled_swap_providers", null))
    }

    @Test
    fun backendProviders_storedByOlderBuild_readFromTheSameKey() {
        preferences.edit { putString("backend_swap_providers", "[]") }

        assertEquals("[]", storage.backendProviders)
    }

    @Test
    fun backendProviders_written_storedUnderTheSameKey() {
        storage.backendProviders = """[{"name":"changelly"}]"""

        assertEquals("""[{"name":"changelly"}]""", preferences.getString("backend_swap_providers", null))
    }
}
