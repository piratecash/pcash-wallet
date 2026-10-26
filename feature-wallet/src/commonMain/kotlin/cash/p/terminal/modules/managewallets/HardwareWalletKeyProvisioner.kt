package cash.p.terminal.modules.managewallets

import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.entities.HardwarePublicKey
import cash.p.terminal.wallet.entities.TokenQuery

/**
 * Reads token public keys from the account's card or device; a device also adds Monero when it is requested.
 * A Trezor adds it only with [moneroRestoreHeight], the height its cold wallet resumes from.
 */
interface HardwareWalletKeyProvisioner {
    suspend fun provisionPublicKeys(
        account: Account,
        tokenQueries: List<TokenQuery>,
        moneroRestoreHeight: Long?,
    ): ProvisionResult
}

sealed interface ProvisionResult {
    /**
     * [account] is the latest copy: adding Monero updates it. [notFoundMessage] replaces the generic text for
     * the tokens without a key; [moneroReady] is false when the device's Monero app could not be read.
     */
    class Success(
        val keys: List<HardwarePublicKey>,
        val account: Account,
        val notFoundMessage: String? = null,
        val moneroReady: Boolean = true,
    ) : ProvisionResult
    data object Cancelled : ProvisionResult
    class Failed(val message: String?) : ProvisionResult
}
