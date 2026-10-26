package cash.p.terminal.core.managers

import cash.p.terminal.tangem.domain.model.AddressBytesWithPublicKey
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.entities.HardwarePublicKey
import io.horizontalsystems.ethereumkit.core.signer.Signer
import io.horizontalsystems.ethereumkit.models.Chain
import io.horizontalsystems.ethereumkit.models.RawTransaction
import io.horizontalsystems.ethereumkit.models.Signature

/** Tangem and Trezor EVM signers, which exist only on platforms with hardware wallet support. */
interface EvmHardwareSigners {
    fun cardSigner(publicKey: HardwarePublicKey, key: AddressBytesWithPublicKey, chain: Chain): Signer

    fun trezorSigner(
        device: AccountType.TrezorDevice,
        publicKey: HardwarePublicKey,
        key: AddressBytesWithPublicKey,
        chain: Chain,
    ): Signer

    /** The transaction a device actually signed with its signature; null when [signer] is not such a device. */
    suspend fun signReconciled(signer: Signer, rawTransaction: RawTransaction): Pair<RawTransaction, Signature>?
}
