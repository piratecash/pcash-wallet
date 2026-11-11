package cash.p.terminal.shared.desktop

import cash.p.terminal.core.adapters.zcash.session.ZcashDatabaseFiles
import cash.p.terminal.core.providers.ZcashDatabaseFilesMarker
import cash.p.terminal.core.providers.ZcashDbMarker
import cash.p.terminal.core.storage.StorageRouter
import cash.p.terminal.shared.DesktopTestGraph
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.koin.core.context.GlobalContext

class DesktopZcashDbMarkerTest {

    @Test
    fun markPristine_throughDesktopGraph_writesTheMarkThatIsPristineReads() {
        DesktopTestGraph.startUnlocked()
        try {
            val koin = GlobalContext.get()
            koin.get<StorageRouter>().prepareAccountStore(ACCOUNT_ID)
            val marker = koin.get<ZcashDbMarker>()
            val databaseFiles = koin.get<ZcashDatabaseFiles>()
            assertFalse(databaseFiles.isPristine(ACCOUNT_ID))

            marker.markPristine(ACCOUNT_ID)

            assertIs<ZcashDatabaseFilesMarker>(marker)
            assertTrue(databaseFiles.isPristine(ACCOUNT_ID))
        } finally {
            DesktopTestGraph.stop()
        }
    }

    private companion object {
        const val ACCOUNT_ID = "account-1"
    }
}
