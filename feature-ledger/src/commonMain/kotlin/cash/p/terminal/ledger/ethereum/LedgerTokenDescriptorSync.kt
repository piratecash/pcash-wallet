package cash.p.terminal.ledger.ethereum

import cash.p.terminal.ledger.account.LedgerSolanaClearSigning
import cash.p.terminal.manager.IConnectivityManager
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.evmChain
import co.touchlab.kermit.Logger
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Caches what a Ledger account's tokens sign in clear with while online, so offline signing has it. */
class LedgerTokenDescriptorSync(
    private val walletManager: IWalletManager,
    private val connectivityManager: IConnectivityManager,
    private val store: LedgerTokenDescriptorStore,
    private val calClient: LedgerCalClient,
    private val solanaClearSigning: LedgerSolanaClearSigning,
    private val dispatcherProvider: DispatcherProvider,
) {
    // A reconnect retries what an offline start or a failed request left missing.
    fun start() {
        dispatcherProvider.applicationScope.launch(dispatcherProvider.io) {
            combine(walletManager.activeWalletsFlow, connectivityManager.isConnected) { wallets, online ->
                if (online) wallets else emptyList()
            }.collect { wallets -> wallets.forEach { fetchMissingLogged(it) } }
        }
    }

    private suspend fun fetchMissingLogged(wallet: Wallet) {
        try {
            fetchMissing(wallet)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            logger.w(error) { "No CAL descriptor for ${wallet.token.type}" }
        }
    }

    private suspend fun fetchMissing(wallet: Wallet) {
        val accountType = wallet.account.type as? AccountType.LedgerDevice ?: return
        when (val tokenType = wallet.token.type) {
            is TokenType.Eip20 -> fetchErc20(wallet, tokenType.address)
            is TokenType.Spl -> solanaClearSigning.prefetch(accountType.model, tokenType.address)
            else -> Unit
        }
    }

    private suspend fun fetchErc20(wallet: Wallet, contract: String) {
        val chainId = wallet.token.blockchainType.evmChain?.id ?: return
        if (store.descriptor(chainId, contract) != null) return
        calClient.erc20Descriptor(chainId, contract)?.let { store.save(chainId, contract, it) }
    }

    private companion object {
        val logger = Logger.withTag("LedgerTokenDescriptorSync")
    }
}
