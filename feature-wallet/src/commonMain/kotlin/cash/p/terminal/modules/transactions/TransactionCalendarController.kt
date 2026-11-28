package cash.p.terminal.modules.transactions

import cash.p.terminal.wallet.managers.TransactionHiddenState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Calendar state shared by the screens with a transaction history: the applied period, the sheet
 * visibility and, for a "to date" period, the date of the oldest transaction of the current selection.
 */
class TransactionCalendarController(
    private val coroutineScope: CoroutineScope,
    private val host: Host,
) {
    var period: TransactionPeriod? = null
        private set
    private var sheetVisible = false
    private var oldestDate: LocalDate? = null
    private var oldestJob: Job? = null

    fun onCalendarClick() = setSheetVisible(true)

    fun onSheetDismiss() = setSheetVisible(false)

    fun apply(period: TransactionPeriod) {
        // A late Apply from a sheet that cleanup already closed (e.g. re-hide) must not restore the period.
        if (!sheetVisible) return
        sheetVisible = false
        setPeriod(period)
    }

    fun clear() = setPeriod(null)

    fun onLeave() {
        sheetVisible = false
        setPeriod(null)
    }

    /** The tab or the token changed: the oldest transaction of the selection may be another one. */
    fun onSelectionChanged() {
        refreshOldest()
        host.onCalendarStateChanged()
    }

    fun uiState(historyExhausted: Boolean) = TransactionPeriodUiState(
        period = period,
        // An oldest transaction after the chosen day would read as a reversed range.
        oldestDate = period?.let { applied -> oldestDate?.takeUnless { it.isAfter(applied.to) } },
        historyExhausted = historyExhausted,
        sheetVisible = sheetVisible,
    )

    private fun setSheetVisible(visible: Boolean) {
        sheetVisible = visible
        host.onCalendarStateChanged()
    }

    private fun setPeriod(newPeriod: TransactionPeriod?) {
        if (newPeriod == period) {
            host.onCalendarStateChanged()
            return
        }
        period = newPeriod
        refreshOldest()
        host.onCalendarStateChanged()
        coroutineScope.launch { host.applyPeriod(newPeriod) }
    }

    private fun refreshOldest() {
        oldestJob?.cancel()
        oldestDate = null
        // Only a "to date" period starts at the oldest transaction.
        if (period?.from != null || period == null) return
        oldestJob = coroutineScope.launch {
            oldestDate = host.oldestTimestamp()?.let {
                Instant.ofEpochSecond(it).atZone(ZoneId.systemDefault()).toLocalDate()
            }
            host.onCalendarStateChanged()
        }
    }

    interface Host {
        fun onCalendarStateChanged()
        suspend fun applyPeriod(period: TransactionPeriod?)

        /** Seconds; null when the selection has no transactions. */
        suspend fun oldestTimestamp(): Long?
    }
}

data class TransactionPeriodUiState(
    val period: TransactionPeriod? = null,
    val oldestDate: LocalDate? = null,
    val historyExhausted: Boolean = false,
    val sheetVisible: Boolean = false,
) {
    val active: Boolean get() = period != null

    /** Only a "for the period" list has an end worth announcing. */
    val showsEndFooter: Boolean get() = period?.from != null && historyExhausted
}

/** Each later change of the hidden flag; `true` means the transactions were hidden again. */
fun Flow<TransactionHiddenState>.hiddenFlagChanges(): Flow<Boolean> =
    map { it.transactionHidden }.distinctUntilChanged().drop(1)
