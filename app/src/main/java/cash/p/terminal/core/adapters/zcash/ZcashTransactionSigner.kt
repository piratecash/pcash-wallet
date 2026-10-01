package cash.p.terminal.core.adapters.zcash

import cash.p.zcash.PreparedTransaction
import cash.p.zcash.ZcashWallet

/** Produces a fully-signed [PreparedTransaction] from one the wallet has already prepared. */
interface ZcashTransactionSigner {

    suspend fun sign(wallet: ZcashWallet, account: Int, transaction: PreparedTransaction): PreparedTransaction
}
