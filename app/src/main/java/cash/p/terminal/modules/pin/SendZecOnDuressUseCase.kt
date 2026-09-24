package cash.p.terminal.modules.pin

import cash.p.terminal.core.ICoinManager
import cash.p.terminal.core.ISendZcashAdapter
import cash.p.terminal.core.LocalizedException
import cash.p.terminal.core.adapters.zcash.zcashErrorName
import cash.p.terminal.core.adapters.zcash.zcashLogger
import cash.p.terminal.core.factories.AdapterFactory
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.core.managers.PendingTransactionRegistrar
import cash.p.terminal.core.managers.broadcasting
import cash.p.terminal.core.toLocalizedString
import cash.p.terminal.modules.send.zcash.zcashPendingDraft
import cash.p.terminal.core.usecase.OfflineModeUseCase
import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IAccountsStorage
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.WalletFactory
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.ISmsNotificationSettings
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel

/**
 * Result of sending ZEC transaction.
 */
sealed class SendZecResult {
    data object Success : SendZecResult()
    data object InsufficientBalance : SendZecResult()
    data object WalletNotFound : SendZecResult()
    data object AdapterCreationFailed : SendZecResult()
    data class TransactionFailed(val error: String) : SendZecResult()
}

/**
 * Use case for sending ZEC transaction when duress mode is activated.
 *
 * When entering duress mode, checks if SMS notification is configured on the previous level.
 * If configured (accountId is set), sends a ZEC transaction to the configured address with the saved memo.
 *
 * Example: Entering level 1 (duress) -> check if accountId is set for level 0
 *          If configured, send ZEC transaction with saved settings from level 0
 */
class SendZecOnDuressUseCase(
    private val smsNotificationSettings: ISmsNotificationSettings,
    private val accountsStorage: IAccountsStorage,
    private val walletManager: IWalletManager,
    private val dispatcherProvider: DispatcherProvider,
    private val adapterFactory: AdapterFactory,
    private val adapterManager: IAdapterManager,
    private val coinManager: ICoinManager,
    private val walletFactory: WalletFactory,
    private val accountManager: IAccountManager,
    private val offlineModeUseCase: OfflineModeUseCase,
) {

    companion object {
        private const val ADAPTER_AWAIT_TIMEOUT_MS = 20000L
    }

    /**
     * Tracks created adapters for cleanup and racing.
     */
    private data class AdapterInfo(
        val adapter: ISendZcashAdapter,
        val wallet: Wallet,
    )

    /**
     * Result of racing adapters for sync.
     */
    private sealed class SyncRaceResult {
        data class Winner(val adapterInfo: AdapterInfo) : SyncRaceResult()
        data object AllSyncedInsufficientBalance : SyncRaceResult()
    }

    /**
     * Sends ZEC transaction if SMS notification is enabled for the previous level.
     * This operation runs asynchronously using application-scoped coroutine to ensure
     * completion even if the PIN unlock screen is closed quickly.
     *
     * @param userLevel The user level that was entered (must be > 0 for duress mode)
     */
    fun sendIfEnabled(userLevel: Int) {
        // Only applies to duress levels (level > 0)
        if (userLevel <= 0) return

        val previousLevel = userLevel - 1
        val accountId = smsNotificationSettings.getSmsNotificationAccountId(previousLevel) ?: return
        val address = smsNotificationSettings.getSmsNotificationAddress(previousLevel) ?: return
        val memo = smsNotificationSettings.getSmsNotificationMemo(previousLevel) ?: ""

        // Use application scope to ensure transaction completes even after screen is closed
        dispatcherProvider.applicationScope.launch(
            CoroutineExceptionHandler { _, exception ->
                zcashLogger.e { "Duress send failed error=${exception.zcashErrorName}" }
            }
        ) {
            try {
                val account = accountsStorage.loadAccount(accountId)
                if (account == null) {
                    zcashLogger.w { "Duress send account not found" }
                    return@launch
                }
                // The account may not be active and its Zcash session may be paused or absent; temporary
                // online keeps the send from being blocked by offline mode for the duress duration.
                offlineModeUseCase.withTemporaryOnline(account, BlockchainType.Zcash) {
                    sendZecTransactionByAccountId(accountId, address, memo)
                }
            } catch (e: Exception) {
                zcashLogger.e { "Duress send failed error=${e.zcashErrorName}" }
            }
        }
    }

    /**
     * Sends a test ZEC transaction with the given wallet and parameters.
     * This is a suspending function that waits for sync and returns the result.
     *
     * @param wallet The wallet to send from
     * @param address The destination address
     * @param memo The memo to include
     * @return SendZecResult indicating success or failure type
     */
    suspend fun sendTestTransaction(
        wallet: Wallet,
        address: String,
        memo: String
    ): SendZecResult {
        return try {
            sendZecTransactionWithWallet(wallet, address, memo)
        } catch (e: Exception) {
            zcashLogger.e { "Test transaction failed error=${e.zcashErrorName}" }
            SendZecResult.TransactionFailed(e.message ?: "Unknown error")
        }
    }

    private suspend fun sendZecTransactionByAccountId(
        accountId: String,
        address: String,
        memo: String
    ): SendZecResult {
        // Find the wallet from the specified level (where it was configured)
        val wallet = findWalletByAccountId(accountId)
        if (wallet == null) {
            zcashLogger.w { "Duress send wallet not found" }
            return SendZecResult.WalletNotFound
        }

        return sendZecTransactionWithWallet(wallet, address, memo)
    }

    private suspend fun sendZecTransactionWithWallet(
        wallet: Wallet,
        address: String,
        memo: String
    ): SendZecResult {
        // First try to get existing adapters for Shielded and Unified from AdapterManager
        val shieldedWallet = createWalletForAddressType(wallet, TokenType.AddressSpecType.Shielded)
        val unifiedWallet = createWalletForAddressType(wallet, TokenType.AddressSpecType.Unified)

        // Check if wallet belongs to active account - if yes, wait for adapter initialization
        val isActiveAccountWallet = wallet.account.id == accountManager.activeAccount?.id &&
                walletManager.getWallets(wallet.account)
                    .find { it.token.blockchainType == BlockchainType.Zcash } != null

        val existingShieldedAdapter: ISendZcashAdapter? = getExistingAdapter(shieldedWallet, isActiveAccountWallet)
        val existingUnifiedAdapter: ISendZcashAdapter? = getExistingAdapter(unifiedWallet, isActiveAccountWallet)

        // If we have existing adapters, use them (race if both exist)
        if (existingShieldedAdapter != null && existingUnifiedAdapter != null) {
            val existingAdapters = listOf(
                AdapterInfo(existingShieldedAdapter, checkNotNull(shieldedWallet)),
                AdapterInfo(existingUnifiedAdapter, checkNotNull(unifiedWallet))
            )
            return when (val result = raceAdaptersForSync(existingAdapters)) {
                is SyncRaceResult.Winner -> {
                    sendWithAdapter(result.adapterInfo, address, memo)
                }

                is SyncRaceResult.AllSyncedInsufficientBalance -> {
                    zcashLogger.w { "Existing adapters have insufficient balance" }
                    SendZecResult.InsufficientBalance
                }
            }
        } else if (existingShieldedAdapter != null) {
            return sendWithAdapter(
                AdapterInfo(existingShieldedAdapter, checkNotNull(shieldedWallet)), address, memo
            )
        } else if (existingUnifiedAdapter != null) {
            return sendWithAdapter(
                AdapterInfo(existingUnifiedAdapter, checkNotNull(unifiedWallet)), address, memo
            )
        }

        return createAndRaceNewAdapters(wallet, address, memo)
    }

    private suspend fun createAndRaceNewAdapters(
        wallet: Wallet,
        address: String,
        memo: String
    ): SendZecResult {
        val createdAdapters = mutableListOf<AdapterInfo>()

        try {
            val (shieldedInfo, unifiedInfo) = coroutineScope {
                val shieldedDeferred = async {
                    createAdapterForAddressType(wallet, TokenType.AddressSpecType.Shielded)
                }
                val unifiedDeferred = async {
                    createAdapterForAddressType(wallet, TokenType.AddressSpecType.Unified)
                }
                Pair(shieldedDeferred.await(), unifiedDeferred.await())
            }

            shieldedInfo?.let { createdAdapters.add(it) }
            unifiedInfo?.let { createdAdapters.add(it) }

            if (createdAdapters.isEmpty()) {
                zcashLogger.w { "Duress send adapter creation failed" }
                return SendZecResult.AdapterCreationFailed
            }

            // Race adapters for sync
            return when (val result = startAndRaceAdaptersForSync(createdAdapters)) {
                is SyncRaceResult.Winner -> {
                    sendWithAdapter(result.adapterInfo, address, memo)
                }

                is SyncRaceResult.AllSyncedInsufficientBalance -> {
                    zcashLogger.w { "Created adapters have insufficient balance" }
                    SendZecResult.InsufficientBalance
                }
            }

        } finally {
            // Always cleanup ALL created adapters
            cleanupAdapters(createdAdapters)
        }
    }


    /**
     * Gets existing adapter for wallet from AdapterManager.
     * If wallet belongs to active account, waits up to 3 seconds for adapter initialization.
     * Otherwise returns immediately (null if not found).
     */
    private suspend fun getExistingAdapter(
        wallet: Wallet?,
        isActiveAccountWallet: Boolean
    ): ISendZcashAdapter? {
        return wallet?.let {
            if (isActiveAccountWallet) {
                adapterManager.awaitAdapterForWallet(it, ADAPTER_AWAIT_TIMEOUT_MS)
            } else {
                adapterManager.getAdapterForWallet(it)
            }
        }
    }

    /**
     * Sends transaction using the provided adapter.
     */
    private suspend fun sendWithAdapter(
        adapterInfo: AdapterInfo,
        address: String,
        memo: String
    ): SendZecResult {
        val adapter = adapterInfo.adapter
        val amountToSend = ISmsNotificationSettings.AMOUNT_TO_SEND_ZEC
        val availableBalance = adapter.maxSpendableBalance

        if (availableBalance < amountToSend) {
            zcashLogger.w { "Duress send has insufficient balance" }
            return SendZecResult.InsufficientBalance
        }

        return try {
            val draft = adapterManager.zcashPendingDraft(
                wallet = adapterInfo.wallet,
                amount = amountToSend,
                fee = adapter.fee.value,
                toAddress = address,
                memo = memo,
                availableBalance = adapter.balanceData.available,
            )
            getKoinInstance<PendingTransactionRegistrar>().broadcasting(draft) {
                adapter.send(amountToSend, address, memo)
            }
            SendZecResult.Success
        } catch (e: Exception) {
            zcashLogger.e { "Duress transaction failed error=${e.zcashErrorName}" }
            val message = (e as? LocalizedException)?.toLocalizedString()
                ?: e.message
                ?: "Transaction failed"
            SendZecResult.TransactionFailed(message)
        }
    }

    /**
     * Creates a Wallet for a specific Zcash address type using the same account.
     */
    private fun createWalletForAddressType(
        sourceWallet: Wallet,
        targetAddressType: TokenType.AddressSpecType
    ): Wallet? {
        val tokenQuery = TokenQuery(
            BlockchainType.Zcash,
            TokenType.AddressSpecTyped(targetAddressType)
        )
        val token = coinManager.getToken(tokenQuery) ?: return null
        return walletFactory.create(token, sourceWallet.account, null)
    }

    /** A detached adapter for this address type; it is never registered in the AdapterManager. */
    private suspend fun createAdapterForAddressType(
        sourceWallet: Wallet,
        addressType: TokenType.AddressSpecType
    ): AdapterInfo? {
        val wallet = createWalletForAddressType(sourceWallet, addressType) ?: return null
        val adapter = adapterFactory.getAdapterOrNull(wallet) as? ISendZcashAdapter ?: return null
        return AdapterInfo(adapter, wallet)
    }

    /**
     * Races adapters to find one synced with sufficient balance.
     * Returns Winner with the first adapter that syncs with sufficient balance,
     * or AllSyncedInsufficientBalance if all adapters sync but none have enough balance.
     */
    private suspend fun raceAdaptersForSync(
        adapters: List<AdapterInfo>
    ): SyncRaceResult = coroutineScope {
        if (adapters.isEmpty()) return@coroutineScope SyncRaceResult.AllSyncedInsufficientBalance

        val amountToSend = ISmsNotificationSettings.AMOUNT_TO_SEND_ZEC
        val resultChannel = Channel<AdapterInfo?>(adapters.size)

        adapters.forEach { info ->
            launch {
                waitForSync(info.adapter)
                val hasSufficientBalance = info.adapter.maxSpendableBalance >= amountToSend
                resultChannel.send(if (hasSufficientBalance) info else null)
            }
        }

        var syncedCount = 0
        while (syncedCount < adapters.size) {
            val result = resultChannel.receive()
            syncedCount++
            if (result != null) {
                coroutineContext.cancelChildren()
                return@coroutineScope SyncRaceResult.Winner(result)
            }
        }

        SyncRaceResult.AllSyncedInsufficientBalance
    }

    /**
     * Starts and races newly created adapters to find one synced with sufficient balance.
     */
    private suspend fun startAndRaceAdaptersForSync(
        adapters: List<AdapterInfo>
    ): SyncRaceResult {
        adapters.forEach { it.adapter.start() }
        return raceAdaptersForSync(adapters)
    }

    /**
     * Waits for an adapter to reach Synced state.
     */
    private suspend fun waitForSync(adapter: ISendZcashAdapter) {
        adapter.balanceStateUpdatedFlow
            .onStart { emit(Unit) }
            .first { adapter.balanceState is AdapterState.Synced }
    }

    /**
     * Stops all created adapters (releases sessions without erasing data).
     */
    private fun cleanupAdapters(adapters: List<AdapterInfo>) {
        adapters.forEach { info ->
            try {
                info.adapter.stop()
            } catch (e: Exception) {
                zcashLogger.w { "Duress adapter stop failed error=${e.zcashErrorName}" }
            }
        }
    }

    private suspend fun findWalletByAccountId(accountId: String): Wallet? {
        // Get accounts from the specified level (where wallet was configured)
        return accountsStorage.loadAccount(accountId)?.let { account ->
            walletManager.getWallets(account)
        }?.find { wallet ->
            wallet.token.blockchainType == BlockchainType.Zcash &&
                    isShieldedOrUnified(wallet.token.type)
        }
    }

    private fun isShieldedOrUnified(tokenType: TokenType): Boolean {
        return when (tokenType) {
            is TokenType.AddressSpecTyped -> {
                tokenType.type == TokenType.AddressSpecType.Shielded ||
                        tokenType.type == TokenType.AddressSpecType.Unified
            }

            else -> false
        }
    }

}
