package cash.p.terminal.modules.tonconnect

import androidx.lifecycle.viewModelScope
import cash.p.terminal.R
import cash.p.terminal.core.App
import cash.p.terminal.core.managers.TonConnectManager
import cash.p.terminal.core.managers.TonKitManager
import cash.p.terminal.core.managers.TonKitWrapper
import cash.p.terminal.core.managers.toTonWalletFullAccess
import cash.p.terminal.core.storage.HardwarePublicKeyStorage
import cash.p.terminal.entities.transactionrecords.ton.TonTransactionRecord
import cash.p.terminal.modules.offline.OfflineOperationGate
import cash.p.terminal.strings.helpers.Translator
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.meta
import cash.p.terminal.wallet.transaction.TransactionSource
import co.touchlab.kermit.Logger
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.extensions.equalsAddress
import com.tonapps.wallet.data.core.entity.RawMessageEntity
import com.tonapps.wallet.data.core.entity.SendRequestEntity
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.ViewModelUiState
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.tonkit.core.TonWallet
import io.horizontalsystems.tonkit.models.Event
import io.horizontalsystems.tonkit.models.SignTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber

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

    private val sendRequestEntity = signTransaction?.request
    private var error: TonConnectSendRequestError? = null
    private val logger = Logger.withTag("TonConnectSendRequestViewModel")
    private val transactionSigner = tonConnectManager.transactionSigner
    private var tonTransactionRecord: TonTransactionRecord? = null

    private var tonWallet: TonWallet.FullAccess? = null
    private var tonKitWrapper: TonKitWrapper? = null
    private var tonEvent: Event? = null
    private var success: Boolean = false

    override fun createState() = TonConnectSendRequestUiState(
        tonTransactionRecord = tonTransactionRecord,
        error = error,
        confirmEnabled = sendRequestEntity != null && tonWallet != null,
        rejectEnabled = sendRequestEntity != null,
        success = success
    )

    init {
        viewModelScope.launch(dispatcherProvider.default) {
            prepareEnv()
            emitState()
        }
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

        if (requestExpired(sendRequestEntity)) {
            error = TonConnectSendRequestError.InvalidData("Field validUntil has expired")

            responseBadRequest(sendRequestEntity)
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

        val (accountId, _) = sendRequestEntity.dAppId.split(":", limit = 2)
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
                Translator.getString(R.string.offline_mode_operation_blocked, TON_NETWORK_NAME)
            )
            responseBadRequest(sendRequestEntity)
            return
        }

        val tonWallet = account.toTonWalletFullAccess(
            hardwarePublicKeyStorage,
            BlockchainType.Ton,
        )
        val tonKitWrapper = try {
            tonKitManager.getNonActiveTonKitWrapper(
                account = account,
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

        val token = App.coinManager.getToken(TokenQuery(BlockchainType.Ton, TokenType.Native))
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

    private fun requestExpired(sendRequestEntity: SendRequestEntity): Boolean {
        return sendRequestEntity.validUntil < System.currentTimeMillis() / 1000
    }

    fun confirm() {
        val sendRequestEntity = sendRequestEntity ?: return
        val tonWallet = tonWallet ?: return

        if (requestExpired(sendRequestEntity)) {
            viewModelScope.launch(dispatcherProvider.default) {
                responseBadRequest(sendRequestEntity)
            }
            throw IllegalArgumentException("Field validUntil has expired")
        }

        viewModelScope.launch(dispatcherProvider.default + CoroutineExceptionHandler { _, ex ->
            Timber.d(ex, "Signing cancelled")
        }) {
            val boc = transactionSigner.sign(sendRequestEntity, tonWallet)

            tonKitWrapper?.tonKit?.send(boc)
            tonConnectManager.kit().approve(sendRequestEntity, boc)

            success = true
            emitState()
        }
    }

    fun reject() {
        val sendRequestEntity = sendRequestEntity ?: return

        viewModelScope.launch(dispatcherProvider.default + CoroutineExceptionHandler { _, ex ->
            logger.w(ex) { "Failed to reject TON Connect request" }
        }) {
            tonConnectManager.kit().reject(sendRequestEntity)
        }
    }
}

private const val TON_NETWORK_NAME = "TON"

sealed class TonConnectSendRequestError : Error() {
    class InvalidData(override val message: String) : TonConnectSendRequestError()
    class EmptySendRequest : TonConnectSendRequestError()
    class AccountNotFound : TonConnectSendRequestError()
    class DifferentAccount(override val message: String) : TonConnectSendRequestError()
    class Other(override val message: String) : TonConnectSendRequestError()
    class KitUnavailable : TonConnectSendRequestError()
}

data class TonConnectSendRequestUiState(
    val tonTransactionRecord: TonTransactionRecord?,
    val error: TonConnectSendRequestError?,
    val confirmEnabled: Boolean,
    val rejectEnabled: Boolean,
    val success: Boolean = false
)
