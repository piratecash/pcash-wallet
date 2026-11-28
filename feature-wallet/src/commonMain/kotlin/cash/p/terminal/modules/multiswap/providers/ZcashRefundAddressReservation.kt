package cash.p.terminal.modules.multiswap.providers

import cash.p.terminal.core.cache.accountScoped
import cash.p.terminal.core.storage.ZcashSingleUseAddressStorage
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.useCases.WalletUseCase
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** No temporary refund address could be reserved; no other address may stand in for it. */
class ZcashRefundAddressUnavailable(cause: Throwable? = null) : Exception(cause)

/**
 * One temporary transparent refund address per visit of the swap screen. A new visit gets a new
 * address unless the previous one was never sent to a provider.
 */
class ZcashRefundAddressReservation(
    private val walletUseCase: WalletUseCase,
    private val accountManager: IAccountManager,
    private val marketKit: MarketKitWrapper,
    private val issuedAddresses: ZcashSingleUseAddressStorage,
) {
    private var sessionAddress: String? by accountManager.accountScoped()
    private val mutex = Mutex()

    fun startSession() {
        sessionAddress = null
    }

    suspend fun current(): String = mutex.withLock {
        sessionAddress ?: reserve().also { sessionAddress = it }
    }

    /** Must run before the request that creates the order: a sent address is never reserved again. */
    suspend fun markSent(address: String) {
        val accountId = accountManager.activeAccount?.id ?: return
        issuedAddresses.incrementUseCount(accountId, address)
    }

    fun transparentToken(): Token? = marketKit.token(
        TokenQuery(BlockchainType.Zcash, TokenType.AddressSpecTyped(TokenType.AddressSpecType.Transparent))
    )

    private suspend fun reserve(): String = try {
        val accountId = requireNotNull(accountManager.activeAccount).id
        issuedAddresses.getUnsentAddress(accountId)?.address ?: checkNotNull(issue())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        throw ZcashRefundAddressUnavailable(e)
    }

    private suspend fun issue(): String? = transparentToken()?.let { walletUseCase.getOneTimeReceiveAddress(it) }
}
