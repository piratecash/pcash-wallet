package cash.p.terminal.ledger.domain

import io.horizontalsystems.hdwalletkit.Utils

interface ICreateLedgerWalletUseCase {
    suspend operator fun invoke(
        accountName: String,
        onOpeningApp: (LedgerAppProgress) -> Unit,
        onExportingViewKey: () -> Unit,
    ): LedgerWalletCreation
}

/** [created] is false when no app gave a key; [skippedApps] lists apps missing or declined on the device. */
data class LedgerWalletCreation(val created: Boolean, val skippedApps: List<String>)

object LedgerDeviceId {
    private const val FINGERPRINT_BYTES = 8

    /** Ledger exposes no serial number, so the device is identified by its first wallet key. */
    @OptIn(ExperimentalStdlibApi::class)
    fun fromWalletPublicKey(walletPublicKey: String): String =
        Utils.sha256(walletPublicKey.encodeToByteArray()).copyOf(FINGERPRINT_BYTES).toHexString()
}
