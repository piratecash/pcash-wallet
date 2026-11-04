package cash.p.terminal.wallet.entities

/** Each version is a separate on-chain account derived from the same key. */
enum class TonWalletVersion(val title: String) {
    V4R2("V4R2"),
    W5("W5"),
}
