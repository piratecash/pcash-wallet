package cash.p.terminal.feature.wallet

import cash.p.terminal.core.TestDispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class LocalizedAssetReaderDesktopTest {

    private val reader = LocalizedAssetReader(TestDispatcherProvider(Dispatchers.Unconfined, CoroutineScope(Dispatchers.Unconfined)))

    @Test
    fun read_languageWithFile_returnsThatTranslation() = runTest {
        val english = reader.read(FaqTopic.PrivateKeys.assetPrefix, "en")
        val russian = reader.read(FaqTopic.PrivateKeys.assetPrefix, "ru")

        assertTrue(english.isNotBlank())
        assertTrue(russian.isNotBlank())
        assertNotEquals(english, russian)
    }

    @Test
    fun read_languageWithoutFile_fallsBackToEnglish() = runTest {
        val english = reader.read("about_premium_", "en")

        assertTrue(english.isNotBlank())
        assertEquals(english, reader.read("about_premium_", "de"))
    }

    @Test
    fun read_unknownPrefix_returnsEmpty() = runTest {
        assertEquals("", reader.read("missing_asset_", "en"))
    }
}
