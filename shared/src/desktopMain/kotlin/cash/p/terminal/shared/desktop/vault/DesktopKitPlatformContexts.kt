package cash.p.terminal.shared.desktop.vault

import cash.p.terminal.core.StorageLockedException
import cash.p.terminal.core.managers.KitPlatformContexts
import cash.p.terminal.core.managers.KitStorageNaming
import java.io.File
import io.horizontalsystems.ethereumkit.PlatformContext as EthereumPlatformContext
import io.horizontalsystems.solanakit.PlatformContext as SolanaPlatformContext
import io.horizontalsystems.stellarkit.PlatformContext as StellarPlatformContext
import io.horizontalsystems.thorchainkit.PlatformContext as ThorchainPlatformContext
import io.horizontalsystems.tonkit.PlatformContext as TonPlatformContext
import io.horizontalsystems.tronkit.PlatformContext as TronPlatformContext

/** Every kit directory lies in the directory of its account: the level the account is visible from plays no part. */
class DesktopKitPlatformContexts(
    private val files: DesktopVaultFiles,
    private val vault: DesktopVault,
) : KitPlatformContexts, KitStorageNaming by DesktopKitStorageNaming(files, vault) {
    override fun ethereum(accountId: String) = EthereumPlatformContext(kitDirectory(accountId, "ethereum"))
    override fun ton(accountId: String) = TonPlatformContext(kitDirectory(accountId, "ton"))

    /** Its own directory: a decoy directory holds one SQLCipher namespace lock, and TON Connect has its own. */
    fun tonConnect(accountId: String) = TonPlatformContext(kitDirectory(accountId, "ton-connect"))
    override fun tron(accountId: String) = TronPlatformContext(kitDirectory(accountId, "tron"))
    override fun solana(accountId: String) = SolanaPlatformContext(kitDirectory(accountId, "solana"))
    override fun stellar(accountId: String) = StellarPlatformContext(kitDirectory(accountId, "stellar"))
    override fun thorchain(accountId: String) = ThorchainPlatformContext(kitDirectory(accountId, "thorchain"))

    override fun bitcoinDatabaseDir(accountId: String): File = kitDirectory(accountId, "bitcoin")
    override fun bitcoinMwebDir(accountId: String): File = kitDirectory(accountId, "bitcoin-mweb")
    override fun zcashBaseDir(accountId: String): File = files.accountStoreDirectory(storageId(accountId))
    override val zcashSharedBaseDir: File get() = files.kitsDirectory
    override fun beamBaseDir(accountId: String): File = files.accountStoreDirectory(storageId(accountId))
    override val beamContainerDir: File get() = files.kitsDirectory

    /** The log names the network in clear text; only the desktop binding can turn it off. */
    override val mwebDebugLogDisabled = true

    private fun kitDirectory(accountId: String, kitName: String) =
        files.kitDirectory(storageId(accountId), kitName).apply { mkdirs() }

    private fun storageId(accountId: String) = vault.storageIdOf(accountId)
}

/** A kit reaches an account only while its store is open: the same condition its database key has. */
internal fun DesktopVault.storageIdOf(accountId: String): String =
    openSession()?.account(accountId)?.storageId ?: throw StorageLockedException()
