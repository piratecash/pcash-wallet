package cash.p.terminal.core.adapters.zcash.session

import cash.p.terminal.wallet.Wallet
import cash.p.zcash.ZcashWallet

/**
 * A wallet database holds several accounts; [dbAccountId] is the one this session speaks for.
 * It is the database row id the SDK hands out, not a zip32 account index.
 */
class OpenedZcashWallet(val wallet: ZcashWallet, val dbAccountId: Int)

/** Turns a pcash wallet into an open [ZcashWallet] — database path, server and keys included. */
interface ZcashWalletOpener {
    suspend fun open(wallet: Wallet): OpenedZcashWallet
}
