package cash.p.terminal.core.managers

import java.io.File
import io.horizontalsystems.ethereumkit.PlatformContext as EthereumPlatformContext
import io.horizontalsystems.solanakit.PlatformContext as SolanaPlatformContext
import io.horizontalsystems.stellarkit.PlatformContext as StellarPlatformContext
import io.horizontalsystems.thorchainkit.PlatformContext as ThorchainPlatformContext
import io.horizontalsystems.tonkit.PlatformContext as TonPlatformContext
import io.horizontalsystems.tronkit.PlatformContext as TronPlatformContext

/** Where each kit keeps an account's files and what it names them; Monero's wallets have their own `MoneroWalletStorage`. */
interface KitPlatformContexts : KitStorageNaming {
    fun ethereum(accountId: String): EthereumPlatformContext
    fun ton(accountId: String): TonPlatformContext
    fun tron(accountId: String): TronPlatformContext
    fun solana(accountId: String): SolanaPlatformContext
    fun stellar(accountId: String): StellarPlatformContext
    fun thorchain(accountId: String): ThorchainPlatformContext

    fun bitcoinDatabaseDir(accountId: String): File
    fun bitcoinMwebDir(accountId: String): File

    /** Parent of the directory with the Zcash wallet database of [accountId]. */
    fun zcashBaseDir(accountId: String): File

    /** Parent of the directory every Zcash wallet shares: the Sapling parameters and the Tor state. */
    val zcashSharedBaseDir: File

    /** Parent of the BEAM wallet directory of [accountId]. */
    fun beamBaseDir(accountId: String): File

    /** Contains every BEAM wallet directory. */
    val beamContainerDir: File

    /** The Android binding of mwebd rejects turning the debug log off. */
    val mwebDebugLogDisabled: Boolean
}
