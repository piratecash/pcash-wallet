package cash.p.terminal.core.managers

import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.logger.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.math.BigInteger

/** Balance-driven: the kit already fetches every denom the address holds. */
class ThorchainAccountManager(
    private val accountManager: IAccountManager,
    private val walletManager: IWalletManager,
    private val thorchainKitManager: ThorchainKitManager,
    private val tokenAutoEnableManager: TokenAutoEnableManager,
    private val userDeletedWalletManager: UserDeletedWalletManager,
    private val marketKit: MarketKitWrapper,
    dispatcherProvider: DispatcherProvider,
) {
    private val blockchainType = thorchainKitManager.blockchainType
    private val logger = AppLogger("thorchain-account-manager")
    private val coroutineScope = CoroutineScope(dispatcherProvider.io)
    private var balanceSubscriptionJob: Job? = null

    fun start() {
        coroutineScope.launch {
            thorchainKitManager.kitStartedFlow.collect { started ->
                handleStarted(started)
            }
        }
    }

    private fun handleStarted(started: Boolean) {
        if (started) {
            subscribeToBalances()
        } else {
            stop()
        }
    }

    private fun stop() {
        balanceSubscriptionJob?.cancel()
    }

    private fun subscribeToBalances() {
        stop()
        val kit = thorchainKitManager.thorchainKitWrapper?.thorchainKit ?: return
        val account = accountManager.activeAccount ?: return

        var previous: Map<String, BigInteger>? = null
        balanceSubscriptionJob = coroutineScope.launch {
            kit.balancesFlow.collect { balances ->
                // Starts from the DB cache: empty means "not synced yet" only before the first non-empty snapshot.
                if (balances.isEmpty() && previous == null) return@collect

                val received = previous?.let { balances.increasedSince(it) } ?: balances
                val initial = previous == null
                previous = balances
                try {
                    handle(received, kit.nativeDenom, account, initial)
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    logger.warning("auto-enable failed", exception)
                }
            }
        }
    }

    internal suspend fun handle(
        balances: Map<String, BigInteger>,
        nativeDenom: String,
        account: Account,
        initial: Boolean,
    ) {
        if (initial && isRestoreGated(account)) return

        val existingTokenTypeIds = walletManager.activeWallets.map { it.token.type.id }
        val newTokenTypes = balances
            .filter { (denom, amount) -> amount > BigInteger.ZERO && denom != nativeDenom }
            .map { (denom, _) -> TokenType.ThorchainAsset(denom) }
            .filter { it.id !in existingTokenTypeIds }

        if (newTokenTypes.isEmpty()) return

        val knownTokens = marketKit.tokensChunked(newTokenTypes.map { TokenQuery(blockchainType, it) })
        val enabledWallets = filterKnownAutoEnableTokens(newTokenTypes, knownTokens).toEnabledWallets(
            accountId = account.id,
            blockchainType = blockchainType,
            userDeletedWalletManager = userDeletedWalletManager,
        )

        if (enabledWallets.isEmpty()) return

        walletManager.saveEnabledWallets(enabledWallets)
    }

    private fun isRestoreGated(account: Account): Boolean =
        account.origin == AccountOrigin.Restored &&
            !account.isWatchAccount &&
            !tokenAutoEnableManager.isAutoEnabled(account, blockchainType)

    // Only a balance that grew is new activity; a later unrelated change must not enable a gated holding.
    private fun Map<String, BigInteger>.increasedSince(previous: Map<String, BigInteger>) =
        filter { (denom, amount) -> amount > (previous[denom] ?: BigInteger.ZERO) }
}
