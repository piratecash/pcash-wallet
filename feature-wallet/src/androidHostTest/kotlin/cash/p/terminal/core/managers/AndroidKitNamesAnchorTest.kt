package cash.p.terminal.core.managers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import cash.p.terminal.core.adapters.zcash.session.ZcashDatabaseFiles
import io.horizontalsystems.databasenaming.DatabaseNameResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Existing installs: a renamed path here means a wallet that silently starts from nothing. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [27])
class AndroidKitNamesAnchorTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val contexts = AndroidKitPlatformContexts(context)
    private val noBackup get() = context.noBackupFilesDir

    @Test
    fun identityNaming_anyAccount_isTheIdentityObjectItself() {
        assertSame(DatabaseNameResolver.Identity, IdentityKitStorageNaming.resolverFor(ACCOUNT_ID))
        assertSame(DatabaseNameResolver.Identity, IdentityKitStorageNaming.sharedResolver)
    }

    @Test
    fun platformContexts_anyAccount_keepTheAndroidPaths() {
        val databases = requireNotNull(context.getDatabasePath("any").parentFile)
        assertEquals(databases, contexts.bitcoinDatabaseDir(ACCOUNT_ID))
        assertEquals(noBackup, contexts.bitcoinMwebDir(ACCOUNT_ID))
        assertEquals(noBackup, contexts.zcashBaseDir(ACCOUNT_ID))
        assertEquals(noBackup, contexts.zcashSharedBaseDir)
        assertEquals(noBackup, contexts.beamBaseDir(ACCOUNT_ID))
        assertEquals(noBackup, contexts.beamContainerDir)
        assertSame(context, contexts.ethereum(ACCOUNT_ID))
        assertFalse(contexts.mwebDebugLogDisabled)
    }

    @Test
    fun zcashDatabaseFiles_anyAccount_keepTheAndroidNames() {
        val files = ZcashDatabaseFiles(contexts, IdentityKitStorageNaming)
        val zcash = File(noBackup, "zcash")

        assertEquals(zcash, files.dataDir)
        assertEquals(zcash, files.databaseDir(ACCOUNT_ID))
        assertEquals(File(noBackup, "co.electricoin.zcash"), files.legacyDir)
        assertEquals(File(zcash, "wallet_$ACCOUNT_ID.sqlite3"), files.databaseFile(ACCOUNT_ID))
        assertEquals(ACCOUNT_ID, files.accountIdOf(files.databaseFile(ACCOUNT_ID).path))
    }

    @Test
    fun beamStorageLocator_anyAccount_keepsTheAndroidPath() {
        val locator = BeamStorageLocator(contexts, IdentityKitStorageNaming)

        val path = locator.storagePath(ACCOUNT_ID)

        assertEquals(File(noBackup, "beam/${locator.storageId(ACCOUNT_ID, BeamNetwork.Mainnet)}"), path)
        assertEquals(File(path, "wallet.db"), locator.databaseFile(ACCOUNT_ID))
    }

    private companion object {
        const val ACCOUNT_ID = "account-id"
    }
}
