package cash.p.terminal.ledger.ethereum

import cash.p.terminal.ledger.domain.LedgerAppAccess
import cash.p.terminal.ledger.domain.LedgerSigningFailedException
import cash.p.terminal.ledger.domain.ledgerPath
import cash.p.terminal.ledger.domain.tokenQuery
import cash.p.terminal.ledgerkit.apdu.LedgerException
import cash.p.terminal.ledgerkit.ethereum.EthereumAppClient
import cash.p.terminal.ledgerkit.ethereum.EthereumSignature
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.crypto.EvmSignatureRecovery
import cash.p.terminal.wallet.entities.HardwarePublicKey
import cash.p.terminal.wallet.hardware.DeviceEvmSigner
import cash.p.terminal.wallet.hardware.ExternalEvmTransactionSigner
import cash.p.terminal.wallet.hardware.SignedEvmTransaction
import co.touchlab.kermit.Logger
import io.horizontalsystems.ethereumkit.crypto.EIP712Encoder
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.Chain
import io.horizontalsystems.ethereumkit.models.RawTransaction
import io.horizontalsystems.ethereumkit.models.Signature

/** Signs on the Ethereum app after the device proved it holds [hardwarePublicKey]; never touches the network. */
class LedgerEvmSigner(
    private val appAccess: LedgerAppAccess,
    private val tokenDescriptors: LedgerTokenDescriptorStore,
    private val account: Account,
    private val hardwarePublicKey: HardwarePublicKey,
    private val address: Address,
    private val chain: Chain,
) : DeviceEvmSigner(address, chain.id), ExternalEvmTransactionSigner {

    private val query = hardwarePublicKey.tokenQuery
    private val path = checkNotNull(ledgerEvmSpec(query)) { "Ledger cannot sign ${query.id}" }.ledgerPath()

    override suspend fun signature(rawTransaction: RawTransaction): Signature =
        throw LedgerSigningFailedException("Use signTransaction() for Ledger")

    override suspend fun signTransaction(rawTransaction: RawTransaction): SignedEvmTransaction {
        val unsigned = EvmSignatureRecovery.unsignedTransaction(rawTransaction, chain.id)
        val descriptor = tokenDescriptors.descriptor(chain.id, rawTransaction.to.hex)
        val deviceSignature = withEthereumApp { client ->
            descriptor?.let { client.provideTokenInformation(it) }
            client.signTransaction(path, unsigned)
        }
        val signature = Signature(
            v = EvmSignatureRecovery.transactionV(deviceSignature.yParity, rawTransaction.gasPrice, chain.id),
            r = deviceSignature.r,
            s = deviceSignature.s,
        )
        if (EvmSignatureRecovery.recoverSenderAddress(rawTransaction, signature, chain.id) != address) {
            throw LedgerSigningFailedException("Device signed with an unexpected account")
        }
        return SignedEvmTransaction(signature, rawTransaction)
    }

    override suspend fun signPersonalMessage(message: ByteArray): ByteArray =
        withEthereumApp { it.signPersonalMessage(path, message) }
            .toMessageSignature(EvmSignatureRecovery.personalSignHash(message))

    override suspend fun signLegacyHash(hash: ByteArray): ByteArray =
        throw LedgerSigningFailedException("eth_sign (raw hash) is not supported on Ledger")

    override suspend fun signTypedDataMessage(rawJson: String): ByteArray {
        // Hashing first rejects malformed data before the device prompt.
        val digest = EIP712Encoder().encodeTypedDataHash(rawJson)
        return withEthereumApp { it.signTypedData(path, rawJson) }.toMessageSignature(digest)
    }

    private suspend fun <T> withEthereumApp(block: suspend (EthereumAppClient) -> T): T =
        appAccess.signWithLedgerApp(account, query) { transport -> block(EthereumAppClient(transport)) }

    // A refused descriptor (stale or for another chain) leaves the transfer to blind signing.
    private suspend fun EthereumAppClient.provideTokenInformation(descriptor: ByteArray) {
        try {
            provideErc20TokenInformation(descriptor)
        } catch (error: LedgerException.InvalidRequest) {
            logger.w(error) { "Ledger refused the token descriptor" }
        }
    }

    private fun EthereumSignature.toMessageSignature(hash: ByteArray): ByteArray =
        EvmSignatureRecovery.messageSignature(hash, r, s, address)
            ?: throw LedgerSigningFailedException("Device signed with an unexpected account")

    private companion object {
        val logger = Logger.withTag("LedgerEvmSigner")
    }
}
