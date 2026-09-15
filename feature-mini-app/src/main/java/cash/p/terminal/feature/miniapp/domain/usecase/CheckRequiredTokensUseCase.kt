package cash.p.terminal.feature.miniapp.domain.usecase

import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.IHardwarePublicKeyStorage
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.TokenQuery
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CheckRequiredTokensUseCase(
    private val walletManager: IWalletManager,
    private val marketKitWrapper: MarketKitWrapper,
    private val hardwarePublicKeyStorage: IHardwarePublicKeyStorage
) {
    data class Result(
        val allTokens: List<Token>,
        val missingTokens: List<Token>,
        val missingTokenQueries: List<TokenQuery>
    ) {
        /** Keyed on queries, not tokens: a required query with no catalog metadata is still missing. */
        val allTokensExist: Boolean get() = missingTokenQueries.isEmpty()
    }

    suspend operator fun invoke(account: Account): Result = withContext(Dispatchers.IO) {
        val requiredQueries = listOf(
            TokenQuery.PirateJetton,
            TokenQuery.PirateCashBnb,
            TokenQuery.CosantaBnb
        )

        val activeWalletTokens = walletManager.getWallets(account).map { it.token }.toSet()
        val tokensByQuery = requiredQueries.associateWith { marketKitWrapper.token(it) }

        val allTokens = tokensByQuery.values.filterNotNull()
        // An enabled wallet does not imply a usable key: a hardware account can have the BSC
        // wallet while its public key is missing, and without that key the connect flow cannot sign.
        val bscKeyMissing = account.isHardwareWalletAccount &&
            hardwarePublicKeyStorage.getKeyByBlockchain(
                account.id,
                BlockchainType.BinanceSmartChain
            ) == null

        val missingPairs = tokensByQuery.filter { (query, token) ->
            (token != null && token !in activeWalletTokens) ||
                (bscKeyMissing && query.blockchainType == BlockchainType.BinanceSmartChain)
        }

        Result(
            allTokens = allTokens,
            missingTokens = missingPairs.values.filterNotNull(),
            missingTokenQueries = missingPairs.keys.toList()
        )
    }
}
