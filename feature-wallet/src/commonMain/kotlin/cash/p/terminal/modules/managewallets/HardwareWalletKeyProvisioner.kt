package cash.p.terminal.modules.managewallets

import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.entities.HardwarePublicKey
import cash.p.terminal.wallet.entities.TokenQuery

/** Reads token public keys from the account's card or device; Trezor also adds Monero when it is requested. */
interface HardwareWalletKeyProvisioner {
    suspend fun provisionPublicKeys(account: Account, tokenQueries: List<TokenQuery>): ProvisionResult
}

sealed interface ProvisionResult {
    /** [account] is the latest copy: adding Monero updates it. */
    class Success(val keys: List<HardwarePublicKey>, val account: Account) : ProvisionResult
    data object Cancelled : ProvisionResult
    class Failed(val message: String?) : ProvisionResult
}
