package cash.p.terminal.core.managers

import cash.p.terminal.wallet.AccountType
import io.horizontalsystems.stellarkit.StellarWallet

fun AccountType.toStellarWallet() = when (this) {
    is AccountType.Mnemonic -> StellarWallet.Seed(seed)
    is AccountType.StellarAddress -> StellarWallet.WatchOnly(address)
    is AccountType.StellarSecretKey -> StellarWallet.SecretKey(key)
    else -> throw IllegalArgumentException(
        "Account type ${this.javaClass.simpleName} can not be converted to StellarWallet.Wallet"
    )
}
