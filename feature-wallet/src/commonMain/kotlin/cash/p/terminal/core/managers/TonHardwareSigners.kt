package cash.p.terminal.core.managers

import cash.p.terminal.wallet.entities.HardwarePublicKey
import com.tonapps.blockchain.ton.contract.HashSigner

/** Tangem TON signer, which exists only on platforms with hardware wallet support. */
interface TonHardwareSigners {
    fun cardHashSigner(hardwarePublicKey: HardwarePublicKey): HashSigner
}
