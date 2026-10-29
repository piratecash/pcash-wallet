package cash.p.terminal.modules.coin.overview

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.assetUrl
import cash.p.terminal.core.description
import cash.p.terminal.core.eip20TokenUrl
import cash.p.terminal.core.isSupported
import cash.p.terminal.core.jettonUrl
import cash.p.terminal.core.order
import cash.p.terminal.core.title
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.modules.chart.ChartIndicatorManager
import cash.p.terminal.modules.coin.CoinViewFactory
import cash.p.terminal.resources.CoinPage_Bips
import cash.p.terminal.resources.CoinPage_Blockchains
import cash.p.terminal.resources.CoinPage_CoinTypes
import cash.p.terminal.resources.CoinPlatforms_Native
import cash.p.terminal.resources.Hud_Added_To_Wallet
import cash.p.terminal.resources.Hud_Removed_From_Wallet
import cash.p.terminal.resources.Res
import cash.p.terminal.strings.helpers.shorten
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.accountTypeDerivation
import cash.p.terminal.wallet.bitcoinCashCoinType
import cash.p.terminal.wallet.entities.FullCoin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.zCashCoinType
import io.horizontalsystems.core.Translator
import io.horizontalsystems.core.imageUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.asFlow
import org.jetbrains.compose.resources.StringResource

class CoinOverviewViewModel(
    private val service: CoinOverviewService,
    private val factory: CoinViewFactory,
    private val walletManager: IWalletManager,
    accountManager: IAccountManager,
    private val chartIndicatorManager: ChartIndicatorManager,
    private val tokenWalletActions: TokenWalletActions,
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    private val _overview = MutableStateFlow<CoinOverviewViewItem?>(null)
    val overview: StateFlow<CoinOverviewViewItem?> = _overview.asStateFlow()
    private val _viewState = MutableStateFlow<ViewState>(ViewState.Loading)
    val viewState: StateFlow<ViewState> = _viewState.asStateFlow()

    var tokenVariants by mutableStateOf<TokenVariants?>(null)
        private set
    var showHudMessage by mutableStateOf<HudMessage?>(null)
        private set

    var chartIndicatorsState by mutableStateOf(
        ChartIndicatorsState(
            hasActiveSubscription = true,
            enabled = chartIndicatorManager.isEnabled
        )
    )

    private var hudMessage: HudMessage? = null
        set(value) {
            field = value
            showHudMessage = value
        }
    private var fullCoin = service.fullCoin
    private var activeAccount = accountManager.activeAccount
    private var activeWallets = walletManager.activeWallets

    init {
        viewModelScope.launch {
            service.coinOverviewObservable.asFlow().collect { coinOverview ->
                _isRefreshing.value = false

                coinOverview.dataOrNull?.let {
                    _overview.value = factory.getOverviewViewItem(it)
                }

                coinOverview.viewState?.let {
                    _viewState.value = it
                }
            }
        }

        service.start()

        viewModelScope.launch {
            walletManager.activeWalletsFlow.collect { wallets ->
                if (wallets.size > activeWallets.size) {
                    hudMessage = HudMessage(Res.string.Hud_Added_To_Wallet, HudMessageType.Success)
                } else if (wallets.size < activeWallets.size) {
                    hudMessage = HudMessage(Res.string.Hud_Removed_From_Wallet, HudMessageType.Error)
                }

                activeWallets = wallets
                refreshTokensVariants()
            }
        }

        refreshTokensVariants()
    }

    fun enableChartIndicators() {
        chartIndicatorManager.enable()
        chartIndicatorsState = chartIndicatorsState.copy(enabled = true)
    }

    fun disableChartIndicators() {
        chartIndicatorManager.disable()
        chartIndicatorsState = chartIndicatorsState.copy(enabled = false)
    }

    fun onHudMessageShown() {
        hudMessage = null
    }

    private fun refreshTokensVariants() {
        tokenVariants = getTokenVariants(fullCoin, activeAccount, activeWallets)
    }

    override fun onCleared() {
        service.stop()
    }

    fun refresh() {
        _isRefreshing.value = true
        service.refresh()
    }

    fun retry() {
        _isRefreshing.value = true
        service.refresh()
    }

    private fun getTokenVariants(
        fullCoin: FullCoin,
        account: Account?,
        activeWallets: List<Wallet>
    ): TokenVariants? {
        val items = mutableListOf<TokenVariant>()
        var type = TokenVariants.Type.Blockchains

        val accountTypeNotWatch = if (account != null && !account.isWatchAccount) {
            account.type
        } else {
            null
        }

        fullCoin.tokens
            .filter {
                when (val tokenType = it.type) {
                    is TokenType.Unsupported -> tokenType.reference.isNotBlank()
                    else -> true
                }
            }
            .sortedWith(
                compareBy<Token> { it.type.order }
                    .thenBy { it.blockchainType.order }
            )
            .forEach { token ->
                val canAddToWallet = accountTypeNotWatch != null
                        && token.isSupported
                        && tokenWalletActions.supports(token, accountTypeNotWatch)

                when (val tokenType = token.type) {
                    is TokenType.Trc10 -> return@forEach
                    is TokenType.ThorchainAsset -> return@forEach

                    is TokenType.Asset -> {
                        val inWallet =
                            canAddToWallet && activeWallets.any { it.token == token }
                        val id = "${tokenType.code}:${tokenType.issuer}"

                        items.add(
                            TokenVariant(
                                value = id.shorten(),
                                copyValue = id,
                                imgUrl = token.blockchainType.imageUrl,
                                explorerUrl = token.blockchain.assetUrl(tokenType.code, tokenType.issuer),
                                name = token.blockchain.name,
                                token = token,
                                canAddToWallet = canAddToWallet,
                                inWallet = inWallet
                            )
                        )
                    }
                    is TokenType.Jetton, is TokenType.TonW5Jetton -> {
                        val address = tokenType.values.reference
                        val inWallet =
                            canAddToWallet && activeWallets.any { it.token == token }
                        items.add(
                            TokenVariant(
                                value = address.shorten(),
                                copyValue = address,
                                imgUrl = token.blockchainType.imageUrl,
                                explorerUrl = token.blockchain.jettonUrl(address),
                                name = token.variantName,
                                token = token,
                                canAddToWallet = canAddToWallet,
                                inWallet = inWallet
                            )
                        )
                    }

                    is TokenType.Eip20 -> {
                        val inWallet =
                            canAddToWallet && activeWallets.any { it.token == token }
                        items.add(
                            TokenVariant(
                                value = tokenType.address.shorten(),
                                copyValue = tokenType.address,
                                imgUrl = token.blockchainType.imageUrl,
                                explorerUrl = token.blockchain.eip20TokenUrl(tokenType.address),
                                name = token.blockchain.name,
                                token = token,
                                canAddToWallet = canAddToWallet,
                                inWallet = inWallet
                            )
                        )
                    }

                    is TokenType.Spl -> {
                        val inWallet =
                            canAddToWallet && activeWallets.any { it.token == token }
                        items.add(
                            TokenVariant(
                                value = tokenType.address.shorten(),
                                copyValue = tokenType.address,
                                imgUrl = token.blockchainType.imageUrl,
                                explorerUrl = token.blockchain.eip20TokenUrl(tokenType.address),
                                name = token.blockchain.name,
                                token = token,
                                canAddToWallet = canAddToWallet,
                                inWallet = inWallet
                            )
                        )
                    }

                    is TokenType.Derived -> {
                        type = TokenVariants.Type.Bips

                        val derivation = tokenType.derivation.accountTypeDerivation

                        val inWallet =
                            canAddToWallet && activeWallets.any { it.token == token }
                        items.add(
                            TokenVariant(
                                value = derivation.addressType + derivation.recommended,
                                copyValue = null,
                                imgUrl = token.blockchainType.imageUrl,
                                explorerUrl = null,
                                name = derivation.rawName,
                                token = token,
                                canAddToWallet = canAddToWallet,
                                inWallet = inWallet,
                            )
                        )
                    }

                    is TokenType.AddressTyped -> {
                        type = TokenVariants.Type.CoinTypes

                        val bchCoinType = tokenType.type.bitcoinCashCoinType

                        val inWallet =
                            canAddToWallet && activeWallets.any { it.token == token }
                        items.add(
                            TokenVariant(
                                value = bchCoinType.title,
                                copyValue = null,
                                imgUrl = token.blockchainType.imageUrl,
                                explorerUrl = null,
                                name = bchCoinType.value,
                                token = token,
                                canAddToWallet = canAddToWallet,
                                inWallet = inWallet
                            )
                        )
                    }

                    is TokenType.AddressSpecTyped -> {
                        type = TokenVariants.Type.CoinTypes

                        val zCashCoinType = tokenType.type.zCashCoinType

                        val inWallet =
                            canAddToWallet && activeWallets.any { it.token == token }
                        items.add(
                            TokenVariant(
                                value = zCashCoinType.title,
                                copyValue = null,
                                imgUrl = token.blockchainType.imageUrl,
                                explorerUrl = null,
                                name = zCashCoinType.value,
                                token = token,
                                canAddToWallet = canAddToWallet,
                                inWallet = inWallet
                            )
                        )
                    }

                    TokenType.Mweb -> {
                        type = TokenVariants.Type.Bips

                        val inWallet =
                            canAddToWallet && activeWallets.any { it.token == token }
                        items.add(
                            TokenVariant(
                                value = tokenType.description,
                                copyValue = null,
                                imgUrl = token.blockchainType.imageUrl,
                                explorerUrl = null,
                                name = tokenType.title,
                                token = token,
                                canAddToWallet = canAddToWallet,
                                inWallet = inWallet,
                            )
                        )
                    }

                    TokenType.Native, TokenType.TonW5Native -> {
                        val inWallet =
                            canAddToWallet && activeWallets.any { it.token == token }
                        items.add(
                            TokenVariant(
                                value = Translator.getString(Res.string.CoinPlatforms_Native),
                                copyValue = null,
                                imgUrl = token.blockchainType.imageUrl,
                                explorerUrl = null,
                                name = token.variantName,
                                token = token,
                                canAddToWallet = canAddToWallet,
                                inWallet = inWallet
                            )
                        )
                    }

                    is TokenType.Unsupported -> {
                        items.add(
                            TokenVariant(
                                value = tokenType.reference.shorten(),
                                copyValue = tokenType.reference,
                                imgUrl = token.blockchainType.imageUrl,
                                explorerUrl = when {
                                    tokenType.reference.isNotBlank() -> token.blockchain.eip20TokenUrl(
                                        tokenType.reference
                                    )

                                    else -> null
                                },
                                name = token.blockchain.name,
                                token = token,
                                canAddToWallet = false,
                                inWallet = false
                            )
                        )
                    }
                }
            }

        return when {
            items.isNotEmpty() -> TokenVariants(items, type)
            else -> null
        }
    }

}

private val Token.variantName: String
    get() = listOf(blockchain.name, title).filter(String::isNotEmpty).joinToString(" ")

data class ChartIndicatorsState(val hasActiveSubscription: Boolean, val enabled: Boolean)

data class TokenVariants(val items: List<TokenVariant>, val type: Type) {
    enum class Type(val titleResId: StringResource) {
        Blockchains(Res.string.CoinPage_Blockchains),
        Bips(Res.string.CoinPage_Bips),
        CoinTypes(Res.string.CoinPage_CoinTypes)
    }
}
