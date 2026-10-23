package cash.p.terminal.core.adapters.zcash

import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.Wallet

/** Trezor signer of the Zcash transparent bundle, which exists only on platforms with hardware wallets. */
interface ZcashHardwareSigners {
    fun trezorSigner(wallet: Wallet, device: AccountType.TrezorDevice): ZcashTransactionSigner
}
