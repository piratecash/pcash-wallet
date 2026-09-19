package cash.p.terminal.domain.usecase

import cash.p.terminal.core.adapters.zcash.session.ZcashDatabaseFiles
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.withContext

/**
 * Drops the databases of the ECC SDK, unreadable since the wallet moved to its own SDK. The Sapling
 * parameters live in the same directory and are still used, so only the `zcash_` aliases go.
 */
class ClearLegacyZcashDataUseCase(
    private val databaseFiles: ZcashDatabaseFiles,
    private val dispatcherProvider: DispatcherProvider,
) {

    suspend operator fun invoke() = withContext(dispatcherProvider.io) {
        databaseFiles.legacyDir
            .listFiles { file -> file.name.startsWith(LEGACY_ALIAS_PREFIX) }
            .orEmpty()
            // `fs_cache` is a directory, the rest are files.
            .forEach { it.deleteRecursively() }
    }

    private companion object {
        const val LEGACY_ALIAS_PREFIX = "zcash_"
    }
}
