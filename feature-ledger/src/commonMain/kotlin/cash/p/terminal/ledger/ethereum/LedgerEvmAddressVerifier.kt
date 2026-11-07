package cash.p.terminal.ledger.ethereum

import cash.p.terminal.ledger.domain.LedgerAddressVerifier
import cash.p.terminal.ledger.domain.ledgerPath
import cash.p.terminal.ledgerkit.ethereum.EthereumAppClient
import cash.p.terminal.ledgerkit.transport.LedgerTransport
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.evmChain

/** Shows the account's EVM address on the device, named after the wallet's chain. */
class LedgerEvmAddressVerifier : LedgerAddressVerifier {

    override fun supports(query: TokenQuery): Boolean = ledgerEvmSpec(query) != null

    override suspend fun verify(transport: LedgerTransport, wallet: Wallet, address: String) {
        val spec = checkNotNull(ledgerEvmSpec(wallet.token.tokenQuery))
        val chainId = wallet.token.blockchainType.evmChain?.id?.toLong()
        EthereumAppClient(transport).getAddress(spec.ledgerPath(), display = true, chainId = chainId)
    }
}
