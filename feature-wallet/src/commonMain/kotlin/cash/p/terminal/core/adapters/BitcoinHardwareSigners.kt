package cash.p.terminal.core.adapters

import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.bitcoincore.AbstractKit
import io.horizontalsystems.bitcoincore.transactions.builder.IInputSigner
import io.horizontalsystems.bitcoincore.transactions.builder.ISchnorrInputSigner
import io.horizontalsystems.core.entities.BlockchainType

/** Tangem and Trezor input signers of the bitcoin-family kits, which exist only on platforms with hardware wallets. */
interface BitcoinHardwareSigners {
    fun cardEcdsaSigner(accountId: String, blockchainType: BlockchainType, tokenType: TokenType): IInputSigner

    fun cardSchnorrSigner(accountId: String, blockchainType: BlockchainType, tokenType: TokenType): ISchnorrInputSigner

    fun trezorSigner(
        accountId: String,
        blockchainType: BlockchainType,
        tokenType: TokenType,
        coin: String,
    ): BitcoinDeviceSigner
}

/** One device signer for both input kinds; it reads each spent input's previous transaction from the kit. */
interface BitcoinDeviceSigner {
    val inputSigner: IInputSigner
    val schnorrInputSigner: ISchnorrInputSigner

    /** Call after the kit is built and before it is returned. */
    fun bindKit(kit: AbstractKit)
}
