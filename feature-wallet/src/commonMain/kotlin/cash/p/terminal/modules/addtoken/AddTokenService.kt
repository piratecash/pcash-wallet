package cash.p.terminal.modules.addtoken

import cash.p.terminal.core.ICoinManager
import cash.p.terminal.core.managers.EvmSyncSourceManager
import cash.p.terminal.core.managers.UserDeletedWalletManager
import cash.p.terminal.core.order
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.WalletFactory
import cash.p.terminal.wallet.customCoinUid
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.entities.TonAsset
import cash.p.terminal.wallet.entities.TonWalletVersion
import cash.p.terminal.wallet.entities.tokenQuery
import cash.p.terminal.wallet.entities.tonAsset
import cash.p.terminal.wallet.entities.withVersion
import cash.p.terminal.wallet.useCases.GetHardwarePublicKeyForWalletUseCase
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.inject

class AddTokenService(
    private val coinManager: ICoinManager,
    private val walletManager: IWalletManager,
    private val accountManager: IAccountManager,
    private val userDeletedWalletManager: UserDeletedWalletManager,
    private val evmSyncSourceManager: EvmSyncSourceManager,
    marketKit: MarketKitWrapper,
) {
    private val getHardwarePublicKeyForWalletUseCase: GetHardwarePublicKeyForWalletUseCase by inject(
        GetHardwarePublicKeyForWalletUseCase::class.java
    )
    private val walletFactory: WalletFactory by inject(WalletFactory::class.java)

    private val blockchainTypes = listOf(
        BlockchainType.Ethereum,
        BlockchainType.BinanceSmartChain,
        BlockchainType.Tron,
        BlockchainType.Ton,
        BlockchainType.Polygon,
        BlockchainType.Avalanche,
        BlockchainType.Gnosis,
        BlockchainType.Fantom,
        BlockchainType.ArbitrumOne,
        BlockchainType.Optimism,
        BlockchainType.Base,
        BlockchainType.ZkSync,
        BlockchainType.RobinhoodChain,
        BlockchainType.Solana
    )

    val blockchains = marketKit
        .blockchains(blockchainTypes.map { it.uid })
        .sortedBy { it.type.order }

    val accountType = accountManager.activeAccount?.type

    suspend fun tokenInfo(blockchain: Blockchain, reference: String): TokenInfo? {
        if (reference.isEmpty()) return null

        val blockchainService = when (blockchain.type) {

            BlockchainType.Tron -> {
                AddTronTokenBlockchainService.getInstance(blockchain)
            }

            BlockchainType.Ton -> {
                AddTonTokenBlockchainService(blockchain)
            }

            BlockchainType.Solana -> {
                AddSolanaTokenBlockchainService.getInstance(blockchain)
            }

            else -> AddEvmTokenBlockchainService.getInstance(blockchain, evmSyncSourceManager)
        }

        if (!blockchainService.isValid(reference)) throw TokenError.InvalidReference

        val token = coinManager.getToken(blockchainService.tokenQuery(reference))
        if (token != null && token.type !is TokenType.Unsupported) {
            return TokenInfo(token, true)
        }

        try {
            val customToken = blockchainService.token(reference)
            return TokenInfo(customToken, false)
        } catch (e: Throwable) {
            throw TokenError.NotFound
        }
    }

    suspend fun addToken(token: TokenInfo) = withContext(Dispatchers.IO) {
        val account = accountManager.activeAccount ?: return@withContext

        val tokens = listOfNotNull(token.token, tonW5Twin(token.token))
        val wallets = tokens.mapNotNull { createWallet(account, it) }
        if (wallets.isNotEmpty()) walletManager.save(wallets)
    }

    private suspend fun createWallet(account: Account, token: Token): Wallet? {
        // Clear deletion flag so previously deleted token can reappear
        userDeletedWalletManager.unmarkAsDeleted(account.id, token.tokenQuery.id)

        val hardwarePublicKey = getHardwarePublicKeyForWalletUseCase(
                account,
                token.blockchainType,
                token.type
            )
        return walletFactory.create(token, account, hardwarePublicKey)
    }

    /** Each TON wallet version holds its own jetton wallets, so an account using W5 gets the jetton there too. */
    private fun tonW5Twin(token: Token): Token? {
        val jetton = (token.tonAsset as? TonAsset.Jetton)?.takeIf { it.version == TonWalletVersion.V4R2 } ?: return null
        val usesW5 = walletManager.activeWallets.any { it.token.tonAsset == TonAsset.Native(TonWalletVersion.W5) }
        if (!usesW5) return null

        val twinQuery = jetton.withVersion(TonWalletVersion.W5).tokenQuery
        return coinManager.getToken(twinQuery)
            ?: token.copy(coin = token.coin.copy(uid = twinQuery.customCoinUid), type = twinQuery.tokenType)
    }

    sealed class TokenError : Exception() {
        object InvalidReference : TokenError()
        object NotFound : TokenError()
    }

    data class TokenInfo(
        val token: Token,
        val inCoinList: Boolean,
    )
}
