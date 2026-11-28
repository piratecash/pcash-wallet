package cash.p.terminal.core.adapters

import cash.p.terminal.tangem.signer.HardwareWalletEcdaSigner
import cash.p.terminal.tangem.signer.HardwareWalletSchnorrSigner
import cash.p.terminal.trezor.signer.TrezorBtcSigner
import cash.p.terminal.trezorkit.client.ITrezorClient
import cash.p.terminal.wallet.IHardwarePublicKeyStorage
import cash.p.terminal.wallet.entities.HardwarePublicKey
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.runBlocking

class AndroidBitcoinHardwareSigners(
    private val hardwarePublicKeyStorage: IHardwarePublicKeyStorage,
    private val trezorClient: ITrezorClient,
) : BitcoinHardwareSigners {

    override fun cardEcdsaSigner(accountId: String, blockchainType: BlockchainType, tokenType: TokenType) =
        HardwareWalletEcdaSigner(hardwarePublicKey = publicKey(accountId, blockchainType, tokenType))

    override fun cardSchnorrSigner(accountId: String, blockchainType: BlockchainType, tokenType: TokenType) =
        HardwareWalletSchnorrSigner(hardwarePublicKey = publicKey(accountId, blockchainType, tokenType))

    override fun trezorSigner(
        accountId: String,
        blockchainType: BlockchainType,
        tokenType: TokenType,
        coin: String,
    ): BitcoinDeviceSigner {
        val previousTransactions = KitBtcPreviousTransactionProvider()
        val signer = TrezorBtcSigner(
            coin = coin,
            derivationPath = publicKey(accountId, blockchainType, tokenType).derivationPath,
            trezorClient = trezorClient
        )
        signer.setPreviousTransactionProvider(previousTransactions)
        return HardwareDeviceBtcSigner(signer, signer, previousTransactions)
    }

    // By token type: a Trezor account may hold several Bitcoin derivations at once.
    private fun publicKey(accountId: String, blockchainType: BlockchainType, tokenType: TokenType): HardwarePublicKey =
        runBlocking { requireNotNull(hardwarePublicKeyStorage.getKey(accountId, blockchainType, tokenType)) }
}
