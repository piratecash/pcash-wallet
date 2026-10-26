package cash.p.terminal.core.managers

import java.io.File
import io.horizontalsystems.ethereumkit.PlatformContext as EthereumPlatformContext
import io.horizontalsystems.solanakit.PlatformContext as SolanaPlatformContext
import io.horizontalsystems.stellarkit.PlatformContext as StellarPlatformContext
import io.horizontalsystems.thorchainkit.PlatformContext as ThorchainPlatformContext
import io.horizontalsystems.tonkit.PlatformContext as TonPlatformContext
import io.horizontalsystems.tronkit.PlatformContext as TronPlatformContext

/** Where each kit keeps its files; Monero's wallets have their own `MoneroWalletStorage`. */
interface KitPlatformContexts {
    val ethereum: EthereumPlatformContext
    val ton: TonPlatformContext
    val tron: TronPlatformContext
    val solana: SolanaPlatformContext
    val stellar: StellarPlatformContext
    val thorchain: ThorchainPlatformContext

    val bitcoinDatabaseDir: File
    val bitcoinMwebDir: File

    /** Parent of the Zcash wallet directory. */
    val zcashBaseDir: File

    /** Parent of the BEAM wallet directory. */
    val beamBaseDir: File
}
