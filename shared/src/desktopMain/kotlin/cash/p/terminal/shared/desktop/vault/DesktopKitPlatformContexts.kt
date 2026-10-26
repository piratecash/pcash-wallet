package cash.p.terminal.shared.desktop.vault

import cash.p.terminal.core.managers.KitPlatformContexts
import java.io.File
import io.horizontalsystems.ethereumkit.PlatformContext as EthereumPlatformContext
import io.horizontalsystems.solanakit.PlatformContext as SolanaPlatformContext
import io.horizontalsystems.stellarkit.PlatformContext as StellarPlatformContext
import io.horizontalsystems.thorchainkit.PlatformContext as ThorchainPlatformContext
import io.horizontalsystems.tonkit.PlatformContext as TonPlatformContext
import io.horizontalsystems.tronkit.PlatformContext as TronPlatformContext

/** Every kit directory lives under the vault's kits directory, so a wipe erases it with the vault. */
class DesktopKitPlatformContexts(private val files: DesktopVaultFiles) : KitPlatformContexts {
    override val ethereum get() = EthereumPlatformContext(kitDirectory("ethereum"))
    override val ton get() = TonPlatformContext(kitDirectory("ton"))
    override val tron get() = TronPlatformContext(kitDirectory("tron"))
    override val solana get() = SolanaPlatformContext(kitDirectory("solana"))
    override val stellar get() = StellarPlatformContext(kitDirectory("stellar"))
    override val thorchain get() = ThorchainPlatformContext(kitDirectory("thorchain"))

    override val bitcoinDatabaseDir: File get() = kitDirectory("bitcoin")
    override val bitcoinMwebDir: File get() = kitDirectory("bitcoin-mweb")
    override val zcashBaseDir: File get() = kitDirectory("zcash")
    override val beamBaseDir: File get() = kitDirectory("beam")

    private fun kitDirectory(name: String) = File(files.kitsDirectory, name).apply { mkdirs() }
}
