package cash.p.terminal.feature.miniapp.domain.usecase

import cash.p.terminal.wallet.Account

/**
 * EIP-191 signing seam. The implementation lives in :app, where the EVM signers are,
 * because this module cannot depend on :app — same boundary as [GetTonAddressUseCase].
 */
interface EvmPersonalSigner {
    /** BSC address of [account], or null when the account type has no EVM address. */
    suspend fun address(account: Account): String?

    /** EIP-191 personal_sign of [message]; 0x-hex, 65 bytes. Null when the account cannot sign. */
    suspend fun signPersonalMessage(account: Account, message: String): String?
}
