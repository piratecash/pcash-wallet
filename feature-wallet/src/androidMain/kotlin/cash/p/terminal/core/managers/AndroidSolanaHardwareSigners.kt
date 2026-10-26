package cash.p.terminal.core.managers

import cash.p.terminal.tangem.signer.HardwareWalletSolanaAccountSigner
import cash.p.terminal.trezor.signer.TrezorSolanaSigner
import cash.p.terminal.trezorkit.client.ITrezorClient
import cash.p.terminal.wallet.entities.HardwarePublicKey
import com.solana.core.Account
import com.solana.core.PublicKey

class AndroidSolanaHardwareSigners(private val trezorClient: ITrezorClient) : SolanaHardwareSigners {

    override fun cardAccount(publicKey: PublicKey, hardwarePublicKey: HardwarePublicKey): Account =
        HardwareWalletSolanaAccountSigner(publicKey = publicKey, hardwarePublicKey = hardwarePublicKey)

    override fun trezorAccount(publicKey: PublicKey, hardwarePublicKey: HardwarePublicKey): Account =
        TrezorSolanaSigner(
            publicKey = publicKey,
            derivationPath = hardwarePublicKey.derivationPath,
            trezorClient = trezorClient
        )
}
