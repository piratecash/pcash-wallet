package cash.p.terminal.core.managers

import cash.p.terminal.tangem.domain.model.AddressBytesWithPublicKey
import cash.p.terminal.tangem.signer.HardwareWalletEvmSigner
import cash.p.terminal.trezor.domain.model.TrezorModel
import cash.p.terminal.trezor.signer.TrezorEvmSigner
import cash.p.terminal.trezorkit.client.ITrezorClient
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.entities.HardwarePublicKey
import cash.p.terminal.wallet.hardware.ExternalEvmTransactionSigner
import io.horizontalsystems.ethereumkit.core.signer.Signer
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.Chain
import io.horizontalsystems.ethereumkit.models.RawTransaction
import io.horizontalsystems.ethereumkit.models.Signature

class AndroidEvmHardwareSigners(private val trezorClient: ITrezorClient) : EvmHardwareSigners {

    override fun cardSigner(publicKey: HardwarePublicKey, key: AddressBytesWithPublicKey, chain: Chain): Signer =
        HardwareWalletEvmSigner(
            address = Address(key.addressBytes),
            publicKey = publicKey,
            chain = chain,
            expectedPublicKeyBytes = key.publicKey
        )

    override fun trezorSigner(
        device: AccountType.TrezorDevice,
        publicKey: HardwarePublicKey,
        key: AddressBytesWithPublicKey,
        chain: Chain,
    ): Signer = TrezorEvmSigner(
        address = Address(key.addressBytes),
        chain = chain,
        derivationPath = publicKey.derivationPath,
        trezorClient = trezorClient,
        model = TrezorModel.fromInternalModel(device.model),
        firmwareVersion = device.firmwareVersion
    )

    override suspend fun signReconciled(
        signer: Signer,
        rawTransaction: RawTransaction,
    ): Pair<RawTransaction, Signature>? =
        (signer as? ExternalEvmTransactionSigner)?.signTransaction(rawTransaction)
            ?.let { it.rawTransaction to it.signature }
}
