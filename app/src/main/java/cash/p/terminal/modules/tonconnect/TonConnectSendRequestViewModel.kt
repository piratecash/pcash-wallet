package cash.p.terminal.modules.tonconnect

import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.ICoinManager
import cash.p.terminal.core.managers.TonConnectManager
import cash.p.terminal.core.managers.TonConnectWalletId
import cash.p.terminal.core.managers.TonKitManager
import cash.p.terminal.core.managers.TonKitWrapper
import cash.p.terminal.core.managers.toTonWalletFullAccess
import cash.p.terminal.core.storage.HardwarePublicKeyStorage
import cash.p.terminal.entities.transactionrecords.ton.TonTransactionRecord
import cash.p.terminal.modules.offline.OfflineOperationGate
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.offline_mode_operation_blocked
import cash.p.terminal.resources.ton_connect_request_expired
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.entities.TonAsset
import cash.p.terminal.wallet.entities.tokenQuery
import cash.p.terminal.wallet.meta
import cash.p.terminal.wallet.transaction.TransactionSource
import co.touchlab.kermit.Logger
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.extensions.equalsAddress
import com.tonapps.wallet.data.core.entity.RawMessageEntity
import com.tonapps.wallet.data.core.entity.SendRequestEntity
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.Translator
import io.horizontalsystems.core.ViewModelUiState
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.tonkit.core.TonWallet
import io.horizontalsystems.tonkit.models.Event
import io.horizontalsystems.tonkit.models.SignTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

class TonConnectSendRequestViewModel(
    private val signTransaction: SignTransaction?,
    private val accountManager: IAccountManager,
    private val tonConnectManager: TonConnectManager,
    private val tonKitManager: TonKitManager,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModelUiState<TonConnectSendRequestUiState>() {

    private val hardwarePublicKeyStorage: HardwarePublicKeyStorage by inject(
        HardwarePublicKeyStorage::class.java
    )
    private val offlineOperationGate: OfflineOperationGate by inject(OfflineOperationGate::class.java)
    private val coinManager: ICoinManager by inject(ICoinManager::class.java)

    private val sendRequestEntity = signTransaction?.request
    private var error: TonConnectSendRequestError? = null
    private val logger = Logger.withTag("TonConnectSendRequestViewModel")
    private val transactionSigner = tonConnectManager.transactionSigner
    private var tonTransactionRecord: TonTransactionRecord? = null

    private var tonWallet: TonWallet.FullAccess? = null
    private var tonKitWrapper: TonKitWrapper? = null
    private var tonEvent: Event? = null
    private var success: Boolean = false
    private var clockOffsetSeconds = 0L

    // Main-confined with confirm() and reject(): the deadline decision and the user's answer cannot interleave.
    private var deadlinePassed = false
    private var answersInProgress = 0

    override fun createState() = TonConnectSendRequestUiState(
        tonTransactionRecord = tonTransactionRecord,
        error = error,
        confirmEnabled = sendRequestEntity != null && tonWallet != null && error == null && !deadlinePassed,
        rejectEnabled = sendRequestEntity != null,
        success = success
    )

    init {
        viewModelScope.launch(dispatcherProvider.default) {
            prepareEnv()
            watchExpiry()
            emitState()
        }
    }

    private fun CoroutineScope.watchExpiry() {
        val request = sendRequestEntity ?: return
        if (error != null || request.validUntil == NO_DEADLINE) return
        launch(dispatcherProvider.main) {
            // Short ticks: a long Handler delay stops counting during deep sleep.
            while (!requestExpired(request)) delay(1.seconds)
            expire(request)
        }
    }

    private suspend fun expire(request: SendRequestEntity) = withContext(dispatcherProvider.main) {
        deadlinePassed = true
        // After the user answered, only the button is switched off: one request gets one answer.
        if (answersInProgress > 0 || error is TonConnectSendRequestError.Expired) {
            emitState()
            return@withContext
        }
        error = TonConnectSendRequestError.Expired()
        emitState()
        try {
            responseBadRequest(request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w(e) { "Failed to report expired TON Connect request" }
        }
    }

    // Nothing left the wallet, so the request counts as unanswered again.
    private suspend fun releaseFailedSigning(request: SendRequestEntity) = withContext(dispatcherProvider.main) {
        answersInProgress--
        if (deadlinePassed) expire(request)
    }

    private suspend fun prepareEnv() {
        if (sendRequestEntity == null) {
            error = TonConnectSendRequestError.EmptySendRequest()
            return
        }

        try {
            sendRequestEntity.network
        } catch (e: Exception) {
            error = TonConnectSendRequestError.InvalidData("Invalid network")
            responseBadRequest(sendRequestEntity)
            return
        }

        validateFromAddress(
            sendRequestEntity,
            signTransaction?.dApp?.accountId
        )?.let { validationError ->
            error = validationError
            responseBadRequest(sendRequestEntity)
            return
        }

        if (validUntilIsInvalid(sendRequestEntity)) {
            error = TonConnectSendRequestError.InvalidData("Invalid validUntil field")

            responseBadRequest(sendRequestEntity)
            return
        }

        tonConnectManager.networkTimeSeconds()?.let { clockOffsetSeconds = it - System.currentTimeMillis() / 1000 }

        if (requestExpired(sendRequestEntity)) {
            expire(sendRequestEntity)
            return
        }

        try {
            val messages = sendRequestEntity.messages
            if (addressIsRaw(messages)) {
                error = TonConnectSendRequestError.InvalidData("Send to Raw address is not allowed")
                responseBadRequest(sendRequestEntity)
                return
            }
        } catch (e: Exception) {
            error = TonConnectSendRequestError.InvalidData("Failed to parse messages")
            responseBadRequest(sendRequestEntity)
            return
        }

        if (isTestnet(sendRequestEntity)) {
            error = TonConnectSendRequestError.InvalidData("Send to Testnet is not allowed")
            responseBadRequest(sendRequestEntity)
            return
        }

        if (sendRequestEntity.messages.isEmpty()) {
            error = TonConnectSendRequestError.InvalidData("Empty messages")
            responseBadRequest(sendRequestEntity)
            return
        }

        val (walletId, _) = sendRequestEntity.dAppId.split(":", limit = 2)
        val (accountId, version) = TonConnectWalletId.parse(walletId)
        val account = accountManager.account(accountId)

        if (account == null) {
            error = TonConnectSendRequestError.AccountNotFound()
            return
        } else if (account != accountManager.activeAccount) {
            error = TonConnectSendRequestError.DifferentAccount("Incorrect account selected")
            responseBadRequest(sendRequestEntity)
            return
        }

        if (offlineOperationGate.isBlocked(account, BlockchainType.Ton)) {
            error = TonConnectSendRequestError.InvalidData(
                Translator.getString(Res.string.offline_mode_operation_blocked, TON_NETWORK_NAME)
            )
            responseBadRequest(sendRequestEntity)
            return
        }

        val tonWallet = account.toTonWalletFullAccess(
            version,
            hardwarePublicKeyStorage,
            BlockchainType.Ton,
        )
        val tonKitWrapper = try {
            tonKitManager.getNonActiveTonKitWrapper(
                account = account,
                version = version,
                blockchainType = BlockchainType.Ton,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w(e) { "TON kit is unavailable for signing" }
            error = TonConnectSendRequestError.KitUnavailable()
            return
        }
        this.tonWallet = tonWallet
        this.tonKitWrapper = tonKitWrapper

        val accountBalance = tonKitWrapper.tonKit.account?.balance
        if (accountBalance != null) {
            val totalSentAmount = sendRequestEntity.messages.sumOf { it.amount }
            if (totalSentAmount > accountBalance) {
                error =
                    TonConnectSendRequestError.InvalidData("Transaction amount exceeds available balance")
                responseBadRequest(sendRequestEntity)
                return
            }
        }

        val tonEvent = try {
            val event = transactionSigner.getDetails(sendRequestEntity, tonWallet)
            tonEvent = event
            event
        } catch (e: Exception) {
            error = TonConnectSendRequestError.InvalidData("Failed to get details")
            responseBadRequest(sendRequestEntity)
            return
        }

        val token = coinManager.getToken(TonAsset.Native(version).tokenQuery)
        if (token == null) {
            error = TonConnectSendRequestError.Other("Token Ton not found")
            return
        }

        val transactionSource = TransactionSource(token.blockchain, account, token.type.meta)

        val tonTransactionConverter = tonConnectManager.adapterFactory.tonTransactionConverter(
            tonKitWrapper.tonKit.receiveAddress,
            transactionSource
        )

        tonTransactionRecord = tonTransactionConverter?.createTransactionRecord(tonEvent)
    }

    /**
     * Validates that the 'from' address in the request matches the connected account address.
     * @return null if valid, or an error if invalid
     */
    private fun validateFromAddress(
        sendRequestEntity: SendRequestEntity,
        connectionAccountId: String?
    ): TonConnectSendRequestError? {
        val requestAccountId = try {
            sendRequestEntity.fromAccountId
        } catch (e: Exception) {
            return TonConnectSendRequestError.InvalidData("Invalid \"from\" address")
        }

        if (requestAccountId != null && connectionAccountId != null
            && !requestAccountId.equalsAddress(connectionAccountId)
        ) {
            return TonConnectSendRequestError.InvalidData(
                "Invalid \"from\" address. Specified wallet address not connected to this app."
            )
        }

        return null
    }

    private fun isTestnet(sendRequestEntity: SendRequestEntity): Boolean {
        return sendRequestEntity.network == TonNetwork.TESTNET
    }

    private fun addressIsRaw(messages: List<RawMessageEntity>): Boolean {
        messages.forEach { message ->
            if (message.addressValue.contains(":", ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    private suspend fun TonConnectSendRequestViewModel.responseBadRequest(entity: SendRequestEntity) {
        withContext(dispatcherProvider.io) {
            tonConnectManager.kit().badRequest(entity)
        }
    }

    private fun validUntilIsInvalid(sendRequestEntity: SendRequestEntity): Boolean {
        return try {
            sendRequestEntity.validUntil
            false
        } catch (e: IllegalArgumentException) {
            true
        }
    }

    private fun networkNowSeconds() = System.currentTimeMillis() / 1000 + clockOffsetSeconds

    private fun requestExpired(request: SendRequestEntity) =
        request.validUntil != NO_DEADLINE && request.validUntil < networkNowSeconds()

    fun confirm() {
        val sendRequestEntity = sendRequestEntity ?: return
        val tonWallet = tonWallet ?: return

        if (deadlinePassed || requestExpired(sendRequestEntity)) {
            if (answersInProgress > 0) return // an earlier attempt may still be sending
            viewModelScope.launch { expire(sendRequestEntity) }
            throw TonConnectSendRequestError.Expired()
        }
        answersInProgress++

        viewModelScope.launch(dispatcherProvider.default + CoroutineExceptionHandler { _, ex ->
            Timber.d(ex, "Signing cancelled")
        }) {
            val boc = try {
                transactionSigner.sign(sendRequestEntity, tonWallet)
            } catch (e: Exception) {
                releaseFailedSigning(sendRequestEntity)
                throw e
            }

            tonKitWrapper?.tonKit?.send(boc)
            tonConnectManager.kit().approve(sendRequestEntity, boc)

            success = true
            emitState()
        }
    }

    fun reject() {
        val sendRequestEntity = sendRequestEntity ?: return
        answersInProgress++

        viewModelScope.launch(dispatcherProvider.default + CoroutineExceptionHandler { _, ex ->
            logger.w(ex) { "Failed to reject TON Connect request" }
        }) {
            tonConnectManager.kit().reject(sendRequestEntity)
        }
    }
}

private const val TON_NETWORK_NAME = "TON"
private const val NO_DEADLINE = 0L

sealed class TonConnectSendRequestError : Error() {
    class InvalidData(override val message: String) : TonConnectSendRequestError()
    class EmptySendRequest : TonConnectSendRequestError()
    class AccountNotFound : TonConnectSendRequestError()
    class DifferentAccount(override val message: String) : TonConnectSendRequestError()
    class Other(override val message: String) : TonConnectSendRequestError()
    class KitUnavailable : TonConnectSendRequestError()
    class Expired : TonConnectSendRequestError() {
        override val message: String = Translator.getString(Res.string.ton_connect_request_expired)
    }
}

data class TonConnectSendRequestUiState(
    val tonTransactionRecord: TonTransactionRecord?,
    val error: TonConnectSendRequestError?,
    val confirmEnabled: Boolean,
    val rejectEnabled: Boolean,
    val success: Boolean = false
)
