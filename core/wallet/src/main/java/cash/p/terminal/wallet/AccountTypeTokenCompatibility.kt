package cash.p.terminal.wallet

import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType

fun AccountType.isCompatibleWith(blockchainType: BlockchainType, tokenType: TokenType): Boolean {
    if (this is AccountType.ThorchainAddress) return blockchainType == BlockchainType.Thorchain
    if (this is AccountType.MayachainAddress) return blockchainType == BlockchainType.Mayachain

    if (blockchainType == BlockchainType.Thorchain || blockchainType == BlockchainType.Mayachain) {
        return this is AccountType.Mnemonic
    }

    return when (this) {
        is AccountType.MnemonicMonero -> {
            blockchainType == BlockchainType.Monero && tokenType == TokenType.Native
        }

        is AccountType.Mnemonic -> {
            tokenType != TokenType.Mweb || blockchainType == BlockchainType.Litecoin
        }

        else -> {
            tokenType != TokenType.Mweb
        }
    }
}
