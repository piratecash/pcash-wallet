package cash.p.terminal.core.providers

import cash.p.terminal.core.adapters.zcash.session.ZcashDatabaseFiles

class ZcashDatabaseFilesMarker(private val databaseFiles: ZcashDatabaseFiles) : ZcashDbMarker {
    override fun markPristine(accountId: String) = databaseFiles.markPristine(accountId)
}
