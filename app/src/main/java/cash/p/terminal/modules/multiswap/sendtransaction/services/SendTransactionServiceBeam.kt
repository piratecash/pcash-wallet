package cash.p.terminal.modules.multiswap.sendtransaction.services

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import cash.p.beam.BeamQuoteRequest
import cash.p.beam.BeamSendAmount
import cash.p.beam.BeamSendContext
import cash.p.beam.BeamSendOperation
import cash.p.beam.BeamSendResolution
import cash.p.beam.BeamTransactionStatus
import cash.p.terminal.R
import cash.p.terminal.core.LocalizedException
import cash.p.terminal.core.adapters.BeamAdapter
import cash.p.terminal.core.ethereum.CautionViewItem
import cash.p.terminal.core.managers.BeamSendCoordinator
import cash.p.terminal.core.managers.BeamSendCoordinator.Outcome
import cash.p.terminal.core.managers.BeamSendCoordinator.Reason
import cash.p.terminal.core.managers.BeamSendCoordinator.SendException
import cash.p.terminal.core.managers.BeamSessionOwner
import cash.p.terminal.core.managers.OfflineModeManager
import cash.p.terminal.entities.CoinValue
import cash.p.terminal.entities.transactionrecords.beam.BeamTransactionRecordConverter
import cash.p.terminal.modules.multiswap.sendtransaction.ISendTransactionService
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionData
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionResult
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionServiceState
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionSettings
import cash.p.terminal.modules.send.SendResult
import cash.p.terminal.modules.send.beam.BeamAmount
import cash.p.terminal.modules.send.beam.BeamRecipient
import cash.p.terminal.modules.send.beam.BeamSendSession
import cash.p.terminal.modules.send.beam.beamSendErrorMessage
import cash.p.terminal.modules.send.beam.isNativeBeamSendWallet
import cash.p.terminal.wallet.Token
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.java.KoinJavaComponent.inject
import java.math.BigDecimal

/**
 * Sends a swap deposit from native BEAM through the same coordinator as the BEAM send screen. One [quote] is one
 * durable SDK operation: it is replaced only before an attempt or once the inventory shows it created nothing.
 */
class SendTransactionServiceBeam(token: Token) : ISendTransactionService<BeamAdapter>(token) {
    private val owner: BeamSessionOwner by inject(BeamSessionOwner::class.java)
    private val coordinator: BeamSendCoordinator by inject(BeamSendCoordinator::class.java)
    private val offlineModeManager: OfflineModeManager by inject(OfflineModeManager::class.java)
    private val dispatcherProvider: DispatcherProvider by inject(DispatcherProvider::class.java)

    private val mutex = Mutex()
    private var data: SendTransactionData.Beam? = null
    private var access: BeamSendSession? = null
    private var quote: BeamSendCoordinator.Quote? = null
    private var cautions = listOf<CautionViewItem>()
    private var loading = true
    // A failed send whose operation could not be looked up: another send could pay twice.
    private var unresolved = false

    override val requiresSendableState = true

    private val _sendTransactionSettingsFlow = MutableStateFlow(SendTransactionSettings.Common)
    override val sendTransactionSettingsFlow: StateFlow<SendTransactionSettings> =
        _sendTransactionSettingsFlow.asStateFlow()

    override fun start(coroutineScope: CoroutineScope) = Unit

    override fun hasSettings() = false

    @Composable
    override fun GetSettingsContent(navController: NavController) = Unit

    override suspend fun setSendTransactionData(data: SendTransactionData) = mutex.withLock {
        // ChangeNow's out-of-range reply carries a placeholder of another chain: nothing to send, not an error.
        this.data = data as? SendTransactionData.Beam
        if (!unresolved) quoteLocked()
        loading = false
        emitState()
    }

    override suspend fun sendTransaction(mevProtectionEnabled: Boolean): SendTransactionResult = mutex.withLock {
        val access = checkNotNull(access)
        val quote = checkNotNull(quote)
        val outcome = try {
            access.confirm(quote)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return@withLock settleFailed(access, quote, error)
        }
        when (outcome) {
            is Outcome.Recorded -> resultFor(outcome.operation)
            Outcome.RetryLater ->
                settleFailed(access, quote, SendException(Reason.NotReady))
        }
    }

    override fun createState() = SendTransactionServiceState(
        availableBalance = adapterManager.getAdjustedBalanceData(wallet)?.available,
        networkFee = quote?.let { getAmountData(CoinValue(feeToken, toBeam(it.value.fee))) },
        cautions = cautions,
        sendable = quote != null && !loading && !unresolved && cautions.isEmpty(),
        loading = loading,
        fields = listOf(),
    )

    private suspend fun quoteLocked() {
        quote = null
        cautions = listOf()
        val data = data ?: return
        val amount = BeamAmount.toAtomic(data.amount)
        val session = owner.current
        cautions = when {
            !BeamRecipient.isSupported(data.address) -> caution(R.string.unsupported_address)
            amount == null -> caution(R.string.beam_send_preview_error)
            !isNativeBeamSendWallet(wallet, session, adapter) -> caution(R.string.beam_send_not_ready)
            else -> try {
                val access = BeamSendSession(
                    wallet, checkNotNull(session), adapter, owner, coordinator, offlineModeManager, dispatcherProvider,
                ).also { access = it }
                val request = BeamQuoteRequest(
                    data.address, BeamSendAmount.Exact(amount), BeamSendContext.Online, data.memo.orEmpty(),
                )
                quote = access.quote(request)
                listOf()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                caution(beamSendErrorMessage(error, attempted = false))
            }
        }
    }

    /** The send did not report an operation; the SDK inventory decides whether one exists. */
    private suspend fun settleFailed(
        access: BeamSendSession,
        quote: BeamSendCoordinator.Quote,
        error: Exception,
    ): SendTransactionResult {
        val operation = try {
            coordinator.recordedFor(access.session).singleOrNull { it.operationId == quote.operationId }
        } catch (lookupError: CancellationException) {
            throw lookupError
        } catch (_: Exception) {
            unresolved = true
            fail(R.string.beam_send_uncertain)
        }
        if (operation != null) return resultFor(operation)
        if ((error as? SendException)?.reason == Reason.QuoteChanged) {
            quoteLocked()
            emitState()
            throw error
        }
        fail(beamSendErrorMessage(error, attempted = false))
    }

    private fun resultFor(operation: BeamSendOperation): SendTransactionResult {
        val uid = BeamTransactionRecordConverter.recordUid(wallet.account.id, operation.transactionId)
        val result = when (val resolution = operation.resolution) {
            is BeamSendResolution.Terminal ->
                if (resolution.status == BeamTransactionStatus.Failed ||
                    resolution.status == BeamTransactionStatus.Canceled
                ) fail(R.string.beam_send_preview_error) else SendResult.Sent(uid)
            is BeamSendResolution.Submitted -> SendResult.Sent(uid)
            // Not terminal: recovery commits Prepared/Committing; Indeterminate is left for Core to report.
            else -> SendResult.SentButQueued(uid)
        }
        return SendTransactionResult.Beam(result, operation.transactionId)
    }

    private fun fail(message: Int): Nothing {
        val error = LocalizedException(message)
        cautions = listOf(createCaution(error))
        emitState()
        throw error
    }

    private fun caution(message: Int) = listOf(createCaution(LocalizedException(message)))

    private fun toBeam(atomic: Long) = BigDecimal.valueOf(atomic, BeamAmount.DECIMALS)
}
