package cash.p.terminal.modules.walletconnect

import cash.p.terminal.core.storage.AppDatabase
import cash.p.terminal.core.storage.liveAccountIds
import cash.p.terminal.core.utils.Utils
import com.reown.walletkit.client.Wallet
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.ActiveAccountState
import cash.p.terminal.modules.walletconnect.storage.WCSessionStorage
import cash.p.terminal.modules.walletconnect.storage.WalletConnectV2Session
import com.reown.walletkit.client.WalletKit
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow

class WCSessionManager(
    private val accountManager: IAccountManager,
    private val storage: WCSessionStorage,
    private val appDatabase: AppDatabase,
    private val dispatcherProvider: DispatcherProvider,
) {

    private val _sessionsFlow = MutableStateFlow<List<Wallet.Model.Session>>(emptyList())
    val sessionsFlow: StateFlow<List<Wallet.Model.Session>>
        get() = _sessionsFlow

    private val _pendingRequestCountFlow = MutableStateFlow(0)
    val pendingRequestCountFlow: StateFlow<Int>
        get() = _pendingRequestCountFlow

    val sessions: List<Wallet.Model.Session>
        get() {
            val accountId = accountManager.activeAccount?.id ?: return emptyList()
            return getSessions(accountId)
        }

    private var requestsQueue = listOf<Wallet.Model.SessionRequest>()

    suspend fun start() {
        // Waiting for WalletKit to be initialized in App
        Utils.waitUntil(5_000, 100) {
            try {
                syncSessions()
                true
            } catch (e: Exception) {
                false
            }
        }


        dispatcherProvider.applicationScope.launch {
            accountManager.activeAccountStateFlow.collect { activeAccountState ->
                if (activeAccountState is ActiveAccountState.ActiveAccount) {
                    syncSessions()
                }
            }
        }

        dispatcherProvider.applicationScope.launch {
            WCDelegate.pendingRequestEvents.collect {
                syncPendingRequest()
            }
        }

        dispatcherProvider.applicationScope.launch {
            accountManager.accountsDeletedFlowable.asFlow().collect {
                handleDeletedAccount()
            }
        }

        dispatcherProvider.applicationScope.launch {
            WCDelegate.walletEvents.collect {
                syncSessions()
            }
        }
    }

    private fun getCurrentSessionRequests(): List<Wallet.Model.SessionRequest> {
        val accountId = accountManager.activeAccount?.id ?: return emptyList()
        return requests(accountId)
    }

    private fun syncSessions() {
        val currentSessions = WCDelegate.getActiveSessions()
        val allDbSessions = storage.getAllSessions()
        disconnectOrphanedSessions(currentSessions, allDbSessions)

        val accountId = accountManager.activeAccount?.id ?: return

        val allDbTopics = allDbSessions.map { it.topic }

        val newSessions = currentSessions.filter { !allDbTopics.contains(it.topic) }
        val deletedTopics = allDbTopics.filter { topic ->
            !currentSessions.any { it.topic == topic }
        }

        storage.save(newSessions.map { WalletConnectV2Session(accountId, it.topic) })
        storage.deleteSessionsByTopics(deletedTopics)

        _sessionsFlow.update { getSessions(accountId) }
        syncPendingRequest()

        syncRequests()
    }

    // A deleted account's rows are kept until its live sessions are disconnected,
    // otherwise syncSessions would bind them to the active account.
    private fun disconnectOrphanedSessions(
        currentSessions: List<Wallet.Model.Session>,
        dbSessions: List<WalletConnectV2Session>,
    ) {
        val liveIds = appDatabase.accountsDao().liveAccountIds().toSet()
        val liveTopics = currentSessions.map { it.topic }.toSet()
        dbSessions
            .filter { it.accountId !in liveIds && it.topic in liveTopics }
            .forEach { WCDelegate.deleteSession(it.topic) }
    }

    private fun syncRequests() {
        requestsQueue = getCurrentSessionRequests()
    }

    private fun getSessions(accountId: String): List<Wallet.Model.Session> {
        val sessions = WalletKit.getListOfActiveSessions()
        val dbSessions = storage.getSessionsByAccountId(accountId)

        val accountSessions = sessions.filter { session ->
            dbSessions.any { it.topic == session.topic }
        }

        return accountSessions
    }

    private fun syncPendingRequest() {
        val requestsCount = accountManager.activeAccount?.let { requests(it.id).size } ?: 0
        _pendingRequestCountFlow.update { requestsCount }
    }

    private fun requests(accountId: String): List<Wallet.Model.SessionRequest> {
        val sessions = getSessions(accountId)
        val pendingRequests = mutableListOf<Wallet.Model.SessionRequest>()
        sessions.forEach { session ->
            pendingRequests.addAll(WalletKit.getPendingListOfSessionRequests(session.topic))
        }
        return pendingRequests
    }

    private fun handleDeletedAccount() {
        syncSessions()
    }

    fun getNewSessionRequest(): Wallet.Model.SessionRequest? {
        val updatedQueue = getCurrentSessionRequests()
        val newRequests = updatedQueue - requestsQueue
        syncRequests()

        return newRequests.firstOrNull()
    }

    open class RequestDataError : Throwable() {
        object UnsupportedChainId : RequestDataError()
        object NoSuitableAccount : RequestDataError()
        object NoSuitableEvmKit : RequestDataError()
        object NoSigner : RequestDataError()
        object RequestNotFoundError : RequestDataError()
        object InvalidGasPrice: RequestDataError()
        object InvalidNonce: RequestDataError()
    }

}
