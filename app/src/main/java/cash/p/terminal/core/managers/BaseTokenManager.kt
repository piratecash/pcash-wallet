package cash.p.terminal.core.managers

import cash.p.terminal.core.ICoinManager
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.wallet.BuildConfig
import cash.p.terminal.wallet.Token
import io.horizontalsystems.core.entities.BlockchainType
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.entities.TokenType.AddressSpecType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class BaseTokenManager(
    private val coinManager: ICoinManager,
    private val localStorage: ILocalStorage,
) {
    val tokens by lazy {
        listOf(
            TokenQuery(BlockchainType.Bitcoin, TokenType.Derived(TokenType.Derivation.Bip84)),
            TokenQuery(BlockchainType.Ethereum, TokenType.Native),
            TokenQuery.eip20(BlockchainType.BinanceSmartChain, BuildConfig.PIRATE_CONTRACT),
            TokenQuery.eip20(BlockchainType.BinanceSmartChain, BuildConfig.COSANTA_CONTRACT),
            TokenQuery(BlockchainType.Zcash, TokenType.AddressSpecTyped(AddressSpecType.Shielded)),
        ).mapNotNull {
            coinManager.getToken(it)
        }
    }

    private val _baseTokenFlow by lazy {
        MutableStateFlow(
            localStorage.balanceTotalCoinUid?.let { balanceTotalCoinUid ->
                tokens.find { it.coin.uid == balanceTotalCoinUid }
            } ?: tokens.firstOrNull()
        )
    }
    val baseTokenFlow: StateFlow<Token?> by lazy { _baseTokenFlow.asStateFlow() }

    val token: Token?
        get() = _baseTokenFlow.value

    fun toggleBaseToken() {
        val indexOfNext = tokens.indexOf(token) + 1
        setBaseToken(tokens.getOrNull(indexOfNext) ?: tokens.firstOrNull())
    }

    fun setBaseTokenQueryId(tokenQueryId: String) {
        val token = TokenQuery.fromId(tokenQueryId)?.let { coinManager.getToken(it) } ?: tokens.first()

        setBaseToken(token)
    }

    private fun setBaseToken(token: Token?) {
        localStorage.balanceTotalCoinUid = token?.coin?.uid

        _baseTokenFlow.update {
            token
        }
    }

}
