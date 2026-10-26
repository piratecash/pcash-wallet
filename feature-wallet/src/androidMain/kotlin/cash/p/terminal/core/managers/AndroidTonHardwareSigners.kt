package cash.p.terminal.core.managers

import cash.p.terminal.tangem.signer.HardwareWalletTonSigner
import cash.p.terminal.wallet.entities.HardwarePublicKey
import com.tonapps.blockchain.ton.contract.HashSigner

class AndroidTonHardwareSigners : TonHardwareSigners {
    override fun cardHashSigner(hardwarePublicKey: HardwarePublicKey): HashSigner =
        HardwareWalletTonSigner(hardwarePublicKey)
}
