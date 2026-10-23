package cash.p.terminal.core.providers

import cash.p.terminal.core.adapters.zcash.ZcashAddressDeriver
import cash.p.terminal.core.adapters.zcash.selectZcashReceiver
import cash.p.terminal.core.adapters.zcash.zcashKey
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.wallet.FallbackAddressProvider
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType

class ZcashFallbackAddressProvider(private val deriver: ZcashAddressDeriver) : FallbackAddressProvider {

    override suspend fun getAddress(wallet: Wallet): String? {
        if (wallet.token.blockchainType != BlockchainType.Zcash) return null
        return tryOrNull { deriveAddress(wallet) }
    }

    private suspend fun deriveAddress(wallet: Wallet): String? {
        val spec = when (val tokenType = wallet.token.type) {
            is TokenType.AddressSpecTyped -> tokenType.type
            TokenType.Native -> null
            else -> return null
        }
        val key = wallet.zcashKey() ?: return null
        return spec.selectZcashReceiver(deriver.addresses(key))
    }
}
