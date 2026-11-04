package cash.p.terminal.core.managers

import cash.p.terminal.wallet.entities.TonWalletVersion

/** TON Connect keeps sessions per wallet id; each TON wallet version of an account is a separate wallet. */
data class TonConnectWalletId(val accountId: String, val version: TonWalletVersion) {
    val value: String
        get() = accountId + version.walletIdSuffix

    companion object {
        fun parse(value: String): TonConnectWalletId {
            // V4R2 has the empty suffix, so the longest matching suffix always exists.
            val version = TonWalletVersion.entries
                .filter { value.endsWith(it.walletIdSuffix) }
                .maxBy { it.walletIdSuffix.length }
            return TonConnectWalletId(value.removeSuffix(version.walletIdSuffix), version)
        }

        fun allOf(accountId: String): List<String> =
            TonWalletVersion.entries.map { TonConnectWalletId(accountId, it).value }
    }
}
