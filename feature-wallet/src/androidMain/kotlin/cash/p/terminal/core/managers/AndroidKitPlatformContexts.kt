package cash.p.terminal.core.managers

import android.content.Context
import java.io.File

/** The kits' Android `PlatformContext` is the application `Context` itself. */
class AndroidKitPlatformContexts(private val context: Context) : KitPlatformContexts {
    override val ethereum get() = context
    override val ton get() = context
    override val tron get() = context
    override val solana get() = context
    override val stellar get() = context
    override val thorchain get() = context

    override val bitcoinDatabaseDir: File
        get() = requireNotNull(context.getDatabasePath(DATABASE_PATH_MARKER).parentFile)
    override val bitcoinMwebDir: File get() = context.noBackupFilesDir
    override val zcashBaseDir: File get() = context.noBackupFilesDir
    override val beamBaseDir: File get() = context.noBackupFilesDir

    private companion object {
        const val DATABASE_PATH_MARKER = "bitcoin-kit-path"
    }
}
