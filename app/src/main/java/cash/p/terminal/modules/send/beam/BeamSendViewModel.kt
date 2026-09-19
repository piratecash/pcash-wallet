package cash.p.terminal.modules.send.beam

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cash.p.beam.BeamAddressType
import cash.p.beam.BeamOfflineSigningState
import cash.p.beam.BeamQuoteRequest
import cash.p.beam.BeamSendAmount
import cash.p.beam.BeamSendContext
import cash.p.beam.BeamSendResolution
import cash.p.beam.BeamWalletState
import cash.p.terminal.R
import cash.p.terminal.core.HSCaution
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.core.adapters.BeamAdapter
import cash.p.terminal.core.managers.BeamSendCoordinator
import cash.p.terminal.core.managers.BeamSendCoordinator.Outcome
import cash.p.terminal.core.managers.BeamSendCoordinator.Reason
import cash.p.terminal.core.managers.BeamSendCoordinator.SendException
import cash.p.terminal.core.managers.BeamSessionOwner
import cash.p.terminal.core.managers.OfflineSignedTransactionRepository
import cash.p.terminal.core.managers.OfflineTransactionPayloadEncoder
import cash.p.terminal.entities.Address
import cash.p.terminal.modules.address.AmountUnique
import cash.p.terminal.modules.send.SendConfirmationData
import cash.p.terminal.modules.send.SendConfirmationFragment.Type
import cash.p.terminal.modules.send.SendFragment.ProceedActionData
import cash.p.terminal.modules.send.SendResult
import cash.p.terminal.modules.send.toggleWalletBalanceWithFeedback
import cash.p.terminal.modules.send.offline.OfflineSignState
import cash.p.terminal.modules.send.offline.OfflineSigningController
import cash.p.terminal.modules.send.offline.OfflineTransactionFormat
import cash.p.terminal.modules.sendtokenselect.PrefilledData
import cash.p.terminal.modules.xrate.XRateService
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import cash.p.terminal.wallet.tokenQueryId
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.CurrencyManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import java.math.BigDecimal

// Both quote pipelines are debounced: the MAX pre-fetch that feeds the amount input's available
// balance, and the Exact quote that feeds the fee cell. Typing must not fire one quote per key.
private const val QUOTE_DEBOUNCE_MS = 400L

// Core's minimum fee for a send into the shielded pool, which every BEAM address type here uses:
// 0.001 base plus 0.01 per shielded output. Inputs are free, so it does not depend on the coins.
private val SEND_FEE: BigDecimal = BigDecimal.valueOf(1_100_000, BeamAmount.DECIMALS)

/**
 * What the form's fee cell may claim. Derived from the request pipeline, never from a precondition:
 * a failed background quote leaves every precondition intact, so a spinner keyed on [canQuote]
 * would never stop. Invariants: Loading implies no quote, Ready implies one.
 */
internal enum class BeamFeeState { Idle, Loading, Ready, Failed }

internal class BeamSendViewModel(
    val wallet: Wallet,
    private val access: BeamSendSession,
    private val offlineOperations: BeamOfflineOperations,
    dispatcherProvider: DispatcherProvider,
    payloadEncoder: OfflineTransactionPayloadEncoder,
    repository: OfflineSignedTransactionRepository,
    private val rates: XRateService,
) : ViewModel() {
    var recipient by mutableStateOf("")
        private set
    var amount by mutableStateOf("")
        private set
    var receiverType by mutableStateOf<BeamAddressType?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var ready by mutableStateOf(false)
        private set
    // The offline sign flow is open (or its quote is in flight): the network is paused until leaveOfflineSign().
    private var offline by mutableStateOf(false)
    var message by mutableStateOf<Int?>(null)
        private set
    var recorded by mutableStateOf(false)
        private set
    var attempted by mutableStateOf(false)
        private set
    var balance by mutableStateOf<BigDecimal?>(null)
        private set
    // A send locks its whole input while the change stays "receiving" until the block, so the live figure
    // drops to zero under the confirmation screen before it closes. It shows the balance at the tap instead.
    private var balanceAtSend by mutableStateOf<BigDecimal?>(null)
    val confirmationBalance: BigDecimal? get() = balanceAtSend ?: balance
    var coinRate by mutableStateOf(rates.getRate(wallet.coin.uid))
        private set
    var amountUnique by mutableStateOf<AmountUnique?>(null)
        private set
    var hideAddress by mutableStateOf(false)
        private set
    var maxSendable by mutableStateOf<BigDecimal?>(null)
        private set
    var feeState by mutableStateOf(BeamFeeState.Idle)
        private set
    private var initialized = false
    private var revision = 0L
    private var quoteJob: Job? = null
    private var pendingOperationId: String? = null
    // The form's live quote: background invalidation and re-quoting only ever touch this one.
    private var preview by mutableStateOf<BeamSendCoordinator.Quote?>(null)
    // Snapshot taken on Next, like every other chain's confirmation screen. A change underneath it
    // is caught on Send by the coordinator's QuoteChanged check, not by swapping the figures.
    private var confirmation by mutableStateOf<BeamSendCoordinator.Quote?>(null)
    private var context by mutableStateOf<BeamOfflineSigningState>(BeamOfflineSigningState.Unavailable)
    private var staged = false
    private var quoteChanged = false

    val signing = OfflineSigningController<Unit>(
        viewModelScope, dispatcherProvider, payloadEncoder, repository,
        { HSCaution(TranslatableString.ResString(errorMessage(it))) },
        { it is CancellationException },
    )
    val quote get() = preview?.value
    val reviewedQuote get() = confirmation?.value
    val operationId get() = pendingOperationId ?: confirmation?.operationId.takeIf { attempted }
    val editable get() = !attempted && !offline && signing.signState != OfflineSignState.Signing
    private val signable get() = editable && access.canSign && receiverType != null
    private val amountValid get() = BeamAmount.parse(amount) != null
    private val canQuoteMax get() = signable && ready
    val canQuote get() = canQuoteMax && amountValid
    val canProceed get() = !busy && !offline && access.current && (quote != null || signable && amountValid)
    val offlineSignSupported get() = access.canSign
    val canSignOffline get() = !busy && signable && amountValid

    /** The SDK's fee-aware Max when it has one, otherwise the local balance less the fixed send fee. */
    val availableToSend: BigDecimal
        get() = maxSendable ?: balance?.let { (it - SEND_FEE).max(BigDecimal.ZERO) } ?: BigDecimal.ZERO
    val feeAmount: BigDecimal? get() = quote?.let { BigDecimal.valueOf(it.fee, BeamAmount.DECIMALS) }
    val feeLoading get() = feeState == BeamFeeState.Loading

    // Resolved lazily, like BaseSendViewModel's `by inject`: the ViewModel is constructed in unit
    // tests where no Koin container is running.
    private val balanceHiddenManager: IBalanceHiddenManager by lazy { getKoinInstance() }
    val balanceHidden: StateFlow<Boolean> by lazy {
        balanceHiddenManager.walletBalanceHiddenFlow(wallet.tokenQueryId)
    }

    fun toggleHideBalance() = balanceHiddenManager.toggleWalletBalanceWithFeedback(wallet.tokenQueryId)

    // Declared above init on purpose: viewModelScope runs on Main.immediate, so the collectors started
    // in init can reach refreshMaxSendable() while the constructor is still running.
    private val maxRequests = MutableStateFlow<Pair<Long, BeamQuoteRequest>?>(null)
    private var maxRequestSeq = 0L
    private val exactRequests = MutableStateFlow<Pair<Long, BeamQuoteRequest>?>(null)
    private var exactRequestSeq = 0L

    // One-shot and bound to the tap that asked for it: keying navigation on `quote` instead would
    // leave the form whenever a background quote landed, including right after the offline switch.
    // The payload travels with the event because `busy` is still set when it is emitted.
    private val proceedEvents = MutableSharedFlow<ProceedActionData>(extraBufferCapacity = 1)
    val proceedRequests: SharedFlow<ProceedActionData> = proceedEvents.asSharedFlow()
    private val offlineSignEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val offlineSignRequests: SharedFlow<Unit> = offlineSignEvents.asSharedFlow()
    // Errors raised by a tap, as string resources; `message` only feeds the confirmation screen's sendResult.
    private val errors = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val errorEvents: SharedFlow<Int> = errors.asSharedFlow()

    // MutableStateFlow conflates equal values, so a repeat request after a balance move would be
    // swallowed without the sequence number.
    private fun refreshMaxSendable() {
        maxSendable = null
        val request = if (canQuoteMax) BeamQuoteRequest(recipient, BeamSendAmount.Max, BeamSendContext.Online) else null
        maxRequests.value = request?.let { ++maxRequestSeq to it }
    }

    private fun refreshExactQuote() {
        val request = if (canQuote) quoteRequest() else null
        exactRequests.value = request?.let { ++exactRequestSeq to it }
        // The Ready arm is defensive, not load-bearing: every current caller runs invalidateQuote()
        // first, so `quote` is null by the time we get here. It keeps a fee already on screen from
        // flickering into a spinner should a later caller schedule without invalidating.
        feeState = when {
            request == null -> BeamFeeState.Idle
            quote != null -> BeamFeeState.Ready
            else -> BeamFeeState.Loading
        }
    }

    // Drives the shared SendConfirmationScreen's button/loader/error cells (see SendResult, SendResultHud).
    // Priority matches the underlying state machine: a send in flight always wins, then a recorded
    // operation is terminal success. Any other non-null message (quote changed, re-enter recipient,
    // uncertain outcome, etc.) is surfaced as Failed so it still reaches the user via SendResultHud's
    // error HUD, even when it was not set by a failed confirm/retry attempt (attempted == false).
    // Cached via derivedStateOf: SendResult.Failed has identity equality and the shared HUD keys
    // effects on this instance, so recomputing a fresh one on every read would restart those effects.
    val sendResult: SendResult? by derivedStateOf {
        val caution = message?.let { HSCaution(TranslatableString.ResString(it)) }
        when {
            busy -> SendResult.Sending
            recorded -> SendResult.Sent()
            caution != null -> SendResult.Failed(caution)
            else -> null
        }
    }

    init {
        viewModelScope.launch { observeWallet() }
        viewModelScope.launch { rates.getRateFlow(wallet.coin.uid).collect { coinRate = it } }
        viewModelScope.launch { observeMaxRequests() }
        viewModelScope.launch { observeExactRequests() }
    }

    // One collector for the whole lifetime: collectLatest cancels a superseded request, so the debounce
    // is the delay below and no nested launch is needed.
    private suspend fun observeExactRequests() {
        exactRequests.collectLatest { entry ->
            if (entry == null) return@collectLatest
            val requestRevision = revision
            delay(QUOTE_DEBOUNCE_MS)
            val result = try {
                access.quote(entry.second)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Silent: the user is still typing, and an amount momentarily above the balance must
                // not flash an error. The message is raised only by an explicit tap.
                if (requestRevision == revision) {
                    feeState = if (quote != null) BeamFeeState.Ready else BeamFeeState.Failed
                }
                return@collectLatest
            }
            if (requestRevision != revision) return@collectLatest
            if (!access.current) {
                if (feeState == BeamFeeState.Loading) feeState = BeamFeeState.Idle
                return@collectLatest
            }
            preview = result
            feeState = BeamFeeState.Ready
        }
    }

    private suspend fun observeMaxRequests() {
        maxRequests.collectLatest { entry ->
            if (entry == null) return@collectLatest
            delay(QUOTE_DEBOUNCE_MS)
            val quoted = try {
                access.quote(entry.second)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                return@collectLatest
            }
            if (!access.current) return@collectLatest
            maxSendable = BigDecimal.valueOf(quoted.value.amount, BeamAmount.DECIMALS)
        }
    }

    private suspend fun observeWallet() {
        combine(access.session.wallet.state, access.session.wallet.balance,
            access.session.wallet.offlineSigningState) { state, balance, context -> Triple(state, balance, context) }
            .collect { (state, value, savedContext) ->
                val wasReady = ready
                val oldBalance = balance
                val oldContext = context
                ready = access.current && state is BeamWalletState.Ready
                balance = if (state == BeamWalletState.Closed) {
                    null
                } else {
                    BigDecimal.valueOf(value.available, BeamAmount.DECIMALS)
                }
                context = savedContext
                // An open offline flow pauses the network on purpose; its quote must survive that.
                val inputsChanged = oldBalance != balance || oldContext != context ||
                    wasReady != ready || !access.current
                if (!attempted && !offline && inputsChanged) {
                    invalidateQuote()
                    refreshMaxSendable()
                    refreshExactQuote()
                }
            }
    }

    fun initialize(prefill: PrefilledData?, hideAddress: Boolean) {
        if (initialized) return
        initialized = true
        this.hideAddress = hideAddress
        onRecipientChanged(prefill?.address.orEmpty())
        prefill?.amount?.let {
            onAmountChanged(it.toPlainString())
            amountUnique = AmountUnique(it)
        }
    }

    fun onRecipientChanged(value: String) {
        if (!editable || recipient == value) return
        recipient = value
        receiverType = BeamRecipient.type(value)
        invalidateQuote()
        refreshMaxSendable()
        refreshExactQuote()
    }

    fun onAmountChanged(value: String) {
        if (!editable || amount == value) return
        amount = value
        invalidateQuote()
        refreshExactQuote()
    }

    fun onEnterAmount(value: BigDecimal?) = onAmountChanged(value?.toPlainString().orEmpty())

    private fun invalidateQuote() {
        revision++
        quoteJob?.cancel()
        quoteJob = null
        // Drops a request still debouncing or in flight. Two callers below never pair this with a
        // refresh, so cancelling here is what stops a stale result arriving for them.
        exactRequests.value = null
        if (!attempted) {
            preview = null
            feeState = BeamFeeState.Idle
        }
        if (!offline) busy = false
        message = null
    }

    fun proceed() {
        if (!canProceed) return
        if (quote != null) {
            emitProceed()
            return
        }
        if (!ready) {
            errors.tryEmit(R.string.beam_send_not_ready)
            return
        }
        if (!canQuote) return
        val request = quoteRequest() ?: return
        // Drop a request still debouncing: this tap quotes the same inputs itself.
        exactRequests.value = null
        busy = true
        quoteJob = viewModelScope.launch { quoteThenProceed(request, revision) }
    }

    /** Split out of [proceed] so the tap's branches and the request's branches are counted apart. */
    private suspend fun quoteThenProceed(request: BeamQuoteRequest, requestRevision: Long) {
        var failed = false
        try {
            val result = access.quote(request)
            if (requestRevision != revision || !access.current) return
            preview = result
            feeState = BeamFeeState.Ready
            // An invalidation raised while the quote was in flight — a balance, context or readiness
            // change — is delivered on the next dispatch, so give it that dispatch before
            // committing to the navigation. Without it the snapshot taken on Next could already be
            // known stale, and Send would only fail with QuoteChanged.
            yield()
            if (requestRevision != revision || !access.current || preview == null) return
            emitProceed()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (requestRevision == revision) {
                failed = true
                errors.tryEmit(errorMessage(error))
                feeState = BeamFeeState.Failed
            }
        } finally {
            if (requestRevision == revision) busy = false
            // Covers a pre-fetch that never landed, and a figure the failed attempt just proved
            // stale. A figure already in hand is left alone.
            if (maxSendable == null || failed) refreshMaxSendable()
        }
    }

    private fun emitProceed() {
        // Once attempted, the snapshot names a durable Core operation; a fresh quote carries another
        // operationId, so replacing it would make retry() prepare a second payment.
        if (!attempted) confirmation = preview
        proceedEvents.tryEmit(
            ProceedActionData(address = recipient.takeIf { it.isNotEmpty() }, wallet = wallet, type = Type.Beam)
        )
    }

    private fun quoteRequest(sendContext: BeamSendContext = BeamSendContext.Online): BeamQuoteRequest? =
        BeamAmount.parse(amount)?.let { BeamQuoteRequest(recipient, BeamSendAmount.Exact(it), sendContext) }

    fun signOffline() {
        if (!canSignOffline) return
        val signingContext = context as? BeamOfflineSigningState.Ready
        if (signingContext == null) {
            errors.tryEmit(R.string.beam_offline_context_unavailable)
            return
        }
        val request = quoteRequest(BeamSendContext.Offline(signingContext.contextId)) ?: return
        // Drops a background online quote so it neither reaches the paused SDK nor lands in the hidden form.
        invalidateQuote()
        offline = true
        busy = true
        // Not quoteJob: invalidateQuote() must not cancel it.
        viewModelScope.launch { quoteOfflineThenOpen(request) }
    }

    /** Opens the sign flow on success; every other exit, cancellation included, resumes the network. */
    private suspend fun quoteOfflineThenOpen(request: BeamQuoteRequest) {
        var opened = false
        try {
            val result = access.quoteOffline(request)
            if (!access.current) return
            confirmation = result
            opened = true
            busy = false
            offlineSignEvents.tryEmit(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            errors.tryEmit(errorMessage(error))
        } finally {
            if (!opened) {
                offline = false
                busy = false
                access.releaseOffline()
            }
        }
    }

    private val actionBlocked get() = busy || recorded || offline || !ready

    fun confirm() {
        val current = confirmation ?: return
        if (actionBlocked || !access.canSign) return
        attempted = true
        pendingOperationId = current.operationId
        balanceAtSend = balance
        perform {
            try {
                accept(access.confirm(current))
            } finally {
                if (!recorded) balanceAtSend = null
            }
        }
    }

    fun retry() {
        if (actionBlocked) return
        val current = confirmation
        if (quoteChanged && current != null) {
            perform {
                confirmation = access.refreshQuote(current)
                preview = confirmation
                quoteChanged = false
                attempted = false
                pendingOperationId = null
                message = R.string.beam_send_quote_changed
            }
            return
        }
        confirm()
    }

    fun onClickSignOffline(format: OfflineTransactionFormat) {
        val current = confirmation
        if (current == null || !offline || !access.canSign) {
            errors.tryEmit(R.string.beam_send_not_ready)
            return
        }
        attempted = true
        pendingOperationId = current.operationId
        signing.exportOwned(format) {
            // A failure after the sign keeps it staged: the next tap only exports, which is byte-identical.
            if (!staged) {
                access.sign(current)
                staged = true
            }
            offlineOperations.export(wallet, current.operationId)
        }
    }

    /** False while a sign/export is in flight: it is never abandoned, so the sign page stays. */
    fun leaveOfflineSign(): Boolean {
        if (signing.signState == OfflineSignState.Signing) return false
        signing.resetSignState()
        val id = operationId
        attempted = false
        staged = false
        pendingOperationId = null
        offline = false
        invalidateQuote()
        refreshMaxSendable()
        refreshExactQuote()
        viewModelScope.launch {
            // Abort strictly before the network resumes. An exported operation returns false and stays listed.
            try {
                if (id != null) access.abort(id)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
            } finally {
                access.releaseOffline()
            }
        }
        return true
    }

    fun confirmationData(): SendConfirmationData {
        val value = checkNotNull(reviewedQuote)
        return SendConfirmationData(
            amount = BigDecimal.valueOf(value.amount, BeamAmount.DECIMALS),
            fee = BigDecimal.valueOf(value.fee, BeamAmount.DECIMALS),
            address = Address(recipient), contact = null, coin = wallet.coin, feeCoin = wallet.coin, memo = null,
        )
    }

    private fun accept(outcome: Outcome) {
        when (outcome) {
            is Outcome.Recorded -> {
                // A recovered operation is not the snapshot's; keeping it would make retry() send the snapshot.
                if (confirmation?.operationId != outcome.operation.operationId) confirmation = null
                attempted = true
                pendingOperationId = outcome.operation.operationId
                recorded = outcome.operation.resolution is BeamSendResolution.Submitted ||
                    outcome.operation.resolution is BeamSendResolution.Terminal
                message = if (recorded) R.string.beam_send_recorded else R.string.beam_send_uncertain
            }
            Outcome.RetryLater -> message = R.string.beam_send_uncertain
        }
    }

    private fun perform(action: suspend () -> Unit) {
        busy = true
        message = null
        viewModelScope.launch {
            try {
                action()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                quoteChanged = (error as? SendException)?.reason == Reason.QuoteChanged
                message = errorMessage(error)
            } finally {
                busy = false
            }
        }
    }

    private fun errorMessage(error: Throwable): Int = beamSendErrorMessage(error, attempted)

    override fun onCleared() {
        access.releaseOffline()
    }

    class Factory(
        private val wallet: Wallet,
        private val session: BeamSessionOwner.Session,
        private val adapter: BeamAdapter,
        private val prefill: PrefilledData?,
        private val hideAddress: Boolean,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val access = BeamSendSession(wallet, session, adapter, getKoinInstance(), getKoinInstance(),
                getKoinInstance(), getKoinInstance())
            val rates = XRateService(getKoinInstance(), getKoinInstance<CurrencyManager>().baseCurrency)
            val viewModel = BeamSendViewModel(wallet, access, getKoinInstance(), getKoinInstance(),
                getKoinInstance(), getKoinInstance(), rates)
            // Initialize at construction time, before the ViewModel is returned to navGraphViewModels:
            // the address input's own ViewModel and hideAddress are read in the very first composition,
            // so recipient/hideAddress must already be set before that composition runs.
            viewModel.initialize(prefill, hideAddress)
            return modelClass.cast(viewModel) ?: error("Invalid BEAM ViewModel class")
        }
    }
}
