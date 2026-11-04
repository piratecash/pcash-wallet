package cash.p.terminal.wallet

import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.entities.TonWalletVersion
import cash.p.terminal.wallet.entities.tonAsset
import io.horizontalsystems.core.entities.BlockchainType

fun AccountType.isCompatibleWith(blockchainType: BlockchainType, tokenType: TokenType): Boolean {
    if (this is AccountType.ThorchainAddress) return blockchainType == BlockchainType.Thorchain
    if (this is AccountType.MayachainAddress) return blockchainType == BlockchainType.Mayachain

    if (blockchainType == BlockchainType.Thorchain || blockchainType == BlockchainType.Mayachain) {
        return this is AccountType.Mnemonic
    }

    if (blockchainType == BlockchainType.Beam) {
        return this is AccountType.Mnemonic && tokenType == TokenType.Native
    }

    return when (this) {
        is AccountType.MnemonicMonero -> {
            blockchainType == BlockchainType.Monero && tokenType == TokenType.Native
        }

        is AccountType.Mnemonic -> {
            tokenType != TokenType.Mweb || blockchainType == BlockchainType.Litecoin
        }

        is AccountType.TonAddress -> isWatchedTonAddressCompatible(blockchainType, tokenType)

        else -> {
            tokenType != TokenType.Mweb
        }
    }
}

/** A watched address is a single account; the app cannot derive another wallet version's address. */
private fun isWatchedTonAddressCompatible(blockchainType: BlockchainType, tokenType: TokenType): Boolean =
    when (TokenQuery(blockchainType, tokenType).tonAsset?.version) {
        TonWalletVersion.W5 -> false
        TonWalletVersion.V4R2, null -> tokenType != TokenType.Mweb
    }
