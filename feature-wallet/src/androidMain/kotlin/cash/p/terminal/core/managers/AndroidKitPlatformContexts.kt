package cash.p.terminal.core.managers

import android.content.Context
import java.io.File

/** The kits' Android `PlatformContext` is the application `Context` itself; an account changes nothing. */
class AndroidKitPlatformContexts(private val context: Context) :
    KitPlatformContexts,
    KitStorageNaming by IdentityKitStorageNaming {
    override fun ethereum(accountId: String) = context
    override fun ton(accountId: String) = context
    override fun tron(accountId: String) = context
    override fun solana(accountId: String) = context
    override fun stellar(accountId: String) = context
    override fun thorchain(accountId: String) = context

    override fun bitcoinDatabaseDir(accountId: String): File =
        requireNotNull(context.getDatabasePath(DATABASE_PATH_MARKER).parentFile)

    override fun bitcoinMwebDir(accountId: String): File = context.noBackupFilesDir
    override fun zcashBaseDir(accountId: String): File = context.noBackupFilesDir
    override val zcashSharedBaseDir: File get() = context.noBackupFilesDir
    override fun beamBaseDir(accountId: String): File = context.noBackupFilesDir
    override val beamContainerDir: File get() = context.noBackupFilesDir
    override val mwebDebugLogDisabled = false

    private companion object {
        const val DATABASE_PATH_MARKER = "bitcoin-kit-path"
    }
}
