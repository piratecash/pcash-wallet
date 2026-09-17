package cash.p.terminal.wallet

enum class MnemonicDerivation(val typeCode: String, val qrValue: String) {
    Legacy("mnemonic", "legacy"),
    Bip39("mnemonic_bip39", "bip39");

    companion object {
        fun fromTypeCode(code: String): MnemonicDerivation? = entries.find { it.typeCode == code }
        fun fromQrValue(value: String?): MnemonicDerivation? = entries.find { it.qrValue == value }
    }
}
