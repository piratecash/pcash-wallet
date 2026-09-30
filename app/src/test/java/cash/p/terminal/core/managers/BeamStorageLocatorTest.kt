package cash.p.terminal.core.managers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFailsWith

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [27])
class BeamStorageLocatorTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val locator = BeamStorageLocator(context)

    @Test
    fun storagePath_untrustedAccountIds_areStableDistinctAndContained() {
        val accounts = listOf("../a", "a", "/a", "a/b", "a_b", "A", "é", "e\u0301", "a\u0000b")
        val paths = accounts.map { locator.storagePath(it) }
        assertEquals(accounts.size, paths.toSet().size)
        accounts.zip(paths).forEach { (account, directory) ->
            assertEquals(directory, BeamStorageLocator(context).storagePath(account))
            assertEquals("mainnet", directory.name)
            assertTrue(directory.parentFile?.name.orEmpty().matches(Regex("[a-f0-9]{64}")))
            val beamRoot = context.noBackupFilesDir.resolve("beam").canonicalFile
            assertEquals(beamRoot, directory.parentFile?.parentFile?.canonicalFile)
            assertEquals(directory.toPath().normalize(), directory.toPath())
            assertEquals(directory.resolve("wallet.db"), locator.databaseFile(account))
        }
    }

    @Test
    fun storagePath_emptyOrMalformedUnicode_rejectsAmbiguousEncoding() {
        listOf("", "\uD800", "\uDC00").forEach { account ->
            assertFailsWith<IllegalArgumentException> { locator.storagePath(account) }
        }
    }
}
