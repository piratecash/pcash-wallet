package io.horizontalsystems.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Locale

class DesktopLanguageManagerTest {

    private val jvmDefault: Locale = Locale.getDefault()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var directory: File
    private lateinit var store: DesktopSettingsStore

    @Before
    fun setUp() {
        Locale.setDefault(Locale.ENGLISH)
        directory = Files.createTempDirectory("language-manager").toFile()
        store = DesktopSettingsStore(File(directory, "settings.preferences_pb"), scope)
    }

    @After
    fun tearDown() {
        Locale.setDefault(jvmDefault)
        scope.cancel()
        directory.deleteRecursively()
    }

    @Test
    fun init_storedLanguage_appliesItToTheJvmDefault() {
        store.languageTag = "ru"

        val manager = DesktopLanguageManager(store)

        assertEquals("ru", Locale.getDefault().language)
        assertEquals("ru", manager.currentLocaleTag)
    }

    @Test
    fun init_noStoredLanguage_keepsTheSystemLanguage() {
        val manager = DesktopLanguageManager(store)

        assertEquals(Locale.ENGLISH.language, Locale.getDefault().language)
        assertEquals(Locale.ENGLISH.toLanguageTag(), manager.currentLocaleTag)
    }

    @Test
    fun reset_storedLanguageApplied_returnsToTheSystemLanguage() {
        store.languageTag = "ru"
        val manager = DesktopLanguageManager(store)

        manager.reset()

        assertEquals(Locale.ENGLISH.language, Locale.getDefault().language)
        assertNull(store.languageTag)
    }
}
