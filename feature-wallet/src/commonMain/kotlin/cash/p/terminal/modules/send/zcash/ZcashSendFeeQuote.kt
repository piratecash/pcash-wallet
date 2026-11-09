package cash.p.terminal.modules.send.zcash

import cash.p.terminal.core.ISendZcashAdapter
import cash.p.terminal.core.ZcashFeeEstimate
import cash.p.terminal.modules.amount.SendAmountService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal

/** Fee and maximum of the send being composed, as the SDK would plan them right now. */
class ZcashSendFeeQuote(private val adapter: ISendZcashAdapter) {

    data class Quote(val fee: BigDecimal, val isCurrent: Boolean, val insufficientBalance: Boolean = false)

    private data class Request(val amount: BigDecimal?, val address: String?, val memo: String)

    private val request = MutableStateFlow(Request(amount = null, address = null, memo = ""))
    private val _quote = MutableStateFlow(Quote(adapter.fee.value, isCurrent = true))
    private val _availableBalance = MutableStateFlow(adapter.maxSpendableBalance)

    val quote = _quote.asStateFlow()
    val availableBalance = _availableBalance.asStateFlow()

    // The flow is hot: a change published before the subscription would be lost.
    private val balanceChanges: Flow<Unit>
        get() = adapter.balanceUpdatedFlow.onStart { emit(Unit) }

    fun start(scope: CoroutineScope) {
        scope.launch { quoteFee() }
        scope.launch { quoteAvailableBalance() }
    }

    fun update(amount: SendAmountService.State, address: SendZCashAddressService.State, memo: String) {
        val next = Request(
            amount = amount.amount?.takeIf { amount.canBeSend },
            address = address.address?.hex?.takeIf { address.canBeSend },
            memo = memo,
        )
        if (next == request.value) return
        _quote.update { it.copy(isCurrent = false) }
        request.value = next
    }

    private suspend fun quoteFee() {
        combine(request, adapter.fee, balanceChanges) { request, adapterFee, _ -> request to adapterFee }
            .collectLatest { (current, adapterFee) ->
                val amount = current.amount
                val address = current.address
                if (amount == null || address == null) {
                    _quote.value = Quote(adapterFee, isCurrent = true)
                    return@collectLatest
                }
                _quote.update { it.copy(isCurrent = false) }
                delay(DEBOUNCE_MS)
                publishUntilAvailable(
                    fallback = { Quote(adapterFee, isCurrent = true) },
                    publish = { if (request.value == current) _quote.value = it },
                ) {
                    when (val estimate = adapter.estimateFee(amount, address, current.memo)) {
                        is ZcashFeeEstimate.Planned -> Quote(estimate.fee, isCurrent = true)
                        ZcashFeeEstimate.InsufficientBalance ->
                            Quote(adapterFee, isCurrent = true, insufficientBalance = true)
                        ZcashFeeEstimate.Unavailable -> null
                    }
                }
            }
    }

    private suspend fun quoteAvailableBalance() {
        val recipient = request.map { it.address to it.memo }.distinctUntilChanged()
        combine(recipient, adapter.fee, balanceChanges) { current, _, _ -> current }
            .collectLatest { (address, memo) ->
                if (address == null) {
                    _availableBalance.value = adapter.maxSpendableBalance
                    return@collectLatest
                }
                delay(DEBOUNCE_MS)
                publishUntilAvailable(
                    fallback = { adapter.maxSpendableBalance },
                    publish = { _availableBalance.value = it },
                ) {
                    adapter.maxSendable(address, memo)
                }
            }
    }

    /** Recovery with an unchanged input emits nothing, so only asking again ends a transient failure. */
    private suspend fun <T : Any> publishUntilAvailable(
        fallback: () -> T,
        publish: (T) -> Unit,
        load: suspend () -> T?,
    ) {
        while (true) {
            val result = load()
            publish(result ?: fallback())
            if (result != null) return
            delay(RETRY_MS)
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 300L
        const val RETRY_MS = 15_000L
    }
}
