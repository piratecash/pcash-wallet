package cash.p.terminal.core.managers

import android.content.Context
import android.net.Uri
import cash.p.terminal.core.factories.AdapterFactory
import cash.p.terminal.core.storage.AppDatabase
import cash.p.terminal.wallet.IAccountManager
import co.touchlab.kermit.Logger
import com.tonapps.wallet.data.tonconnect.entities.DAppEntity
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.tonkit.core.TonKit
import io.horizontalsystems.tonkit.models.Network
import io.horizontalsystems.tonkit.models.SignTransaction
import io.horizontalsystems.tonkit.tonconnect.TonConnectKit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class TonConnectManager(
    private val context: Context,
    val adapterFactory: AdapterFactory,
    private val appName: String,
    private val appVersion: String,
    private val databaseKeyProvider: TonConnectDatabaseKeyProvider,
    private val accountManager: IAccountManager,
    private val appDatabase: AppDatabase,
    private val dispatcherProvider: DispatcherProvider,
) {
    private val logger = Logger.withTag("TonConnectManager")
    private val kitMutex = Mutex()
    private val _kitState = MutableStateFlow<TonConnectKit?>(null)
    val kitState: StateFlow<TonConnectKit?> = _kitState.asStateFlow()

    val transactionSigner = TonKit.getTransactionSigner(TonKit.getTonApi(Network.MainNet))

    val sendRequestFlow: Flow<SignTransaction> =
        kitState.filterNotNull().flatMapLatest { it.sendRequestFlow }
    private val _dappRequestFlow = MutableSharedFlow<DAppRequest>()
    val dappRequestFlow
        get() = _dappRequestFlow.asSharedFlow()

    fun getDApps(): Flow<List<DAppEntity>> = kitState.filterNotNull().flatMapLatest { it.getDApps() }

    fun start() {
        dispatcherProvider.applicationScope.launch {
            logFailure("initialize") { kit() }
        }
        dispatcherProvider.applicationScope.launch {
            accountManager.accountsDeletedFlowable.asFlow().collect {
                logFailure("remove dApps of deleted accounts") {
                    kit().removeDAppsExcept(liveAccountIds())
                }
            }
        }
    }

    /** Creates the kit on first use; a failure is rethrown and not cached, so the next call retries. */
    suspend fun kit(): TonConnectKit = kitMutex.withLock {
        _kitState.value ?: try {
            createKit().also { _kitState.value = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w(e) { "TON Connect initialization failed" }
            throw e
        }
    }

    private suspend fun createKit(): TonConnectKit {
        val databaseKey = databaseKeyProvider.awaitKey(TON_CONNECT_DATABASE_ID)
        TonConnectKit.migrateDatabase(context, databaseKey)
        val kit = TonConnectKit.getInstance(context, databaseKey, appName, appVersion)
        // Sessions of accounts deleted before this version would otherwise stay subscribed.
        kit.removeDAppsExcept(liveAccountIds())
        kit.start()
        return kit
    }

    // Every access level: accountManager.accounts holds only the current level's accounts.
    private suspend fun liveAccountIds(): List<String> = withContext(dispatcherProvider.io) {
        val accountsDao = appDatabase.accountsDao()
        accountsDao.getIds() - accountsDao.getDeletedIds().toSet()
    }

    private suspend fun logFailure(action: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w(e) { "Failed to $action" }
        }
    }

    suspend fun handle(scannedText: String, closeAppOnResult: Boolean = false) {
        try {
            TonConnectKit.readData(scannedText)
            _dappRequestFlow.emit(DAppRequest(scannedText, closeAppOnResult))
        } catch (e: Throwable) {
            // The parser's message embeds the untrusted payload.
            logger.w { "Invalid TON Connect request: ${e::class.simpleName}" }
        }
    }

    companion object {
        const val TON_CONNECT_DATABASE_ID = "ton-connect"
    }
}

data class DAppRequest(
    val uri: String,
    val closeAppOnResult: Boolean
)

/**
 * Detects whether a URI is a TON Connect deeplink in one of the supported formats:
 *  - `tc:...`              — Tonkeeper-compatible scheme
 *  - `pcash.money:...`     — legacy P.cash scheme
 *  - `pcash://ton-connect` — current P.cash scheme
 */
fun Uri.isTonConnectDeeplink(): Boolean {
    val raw = toString()
    return raw.startsWith("pcash.money:")
        || raw.startsWith("tc:")
        || (scheme == "pcash" && host == "ton-connect")
}
