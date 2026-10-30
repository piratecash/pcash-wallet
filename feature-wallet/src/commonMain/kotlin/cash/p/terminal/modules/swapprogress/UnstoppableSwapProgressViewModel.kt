package cash.p.terminal.modules.swapprogress

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.core.usecase.UpdateSwapProviderTransactionsStatusUseCase
import cash.p.terminal.entities.SwapProviderTransaction
import cash.p.terminal.modules.transactionInfo.TransactionInfoViewItem
import cash.p.terminal.network.unstoppable.domain.entity.UnstoppableTrack
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import cash.p.terminal.network.unstoppable.domain.repository.UnstoppableRepository
import co.touchlab.kermit.Logger
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

data class UnstoppableSwapProgressUiState(
    val loading: Boolean = true,
    val hideAmount: Boolean = false,
    val sections: List<List<TransactionInfoViewItem>> = emptyList(),
)

class UnstoppableSwapProgressViewModel(
    private val swapDate: Long,
    private val transactionUid: String,
    private val walletUid: String?,
    private val storage: SwapProviderTransactionsStorage,
    private val unstoppableRepository: UnstoppableRepository,
    private val updateSwapProviderTransactionsStatusUseCase: UpdateSwapProviderTransactionsStatusUseCase,
    private val stepsFactory: UnstoppableSwapStepsFactory,
    private val balanceHiddenManager: IBalanceHiddenManager,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    var uiState by mutableStateOf(UnstoppableSwapProgressUiState())
        private set

    private val logger = Logger.withTag("UnstoppableSwapProgressVM")
    private val swap = MutableStateFlow<SwapProviderTransaction?>(null)
    private val firstRefreshDone = MutableStateFlow(false)
    private val contacts = MutableStateFlow<List<SupportContact>>(emptyList())
    private var contactsJob: Job? = null
    private var pollJob: Job? = null

    init {
        viewModelScope.launch {
            launch { storage.observeByDate(swapDate).filterNotNull().collect { swap.value = it } }
            loadContactsIfMissing()
            observeSections()
        }
    }

    fun toggleBalanceVisibility() = balanceHiddenManager.toggleTransactionInfoHidden(transactionUid)

    fun onResume() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                loadContactsIfMissing()
                refresh()
                delay(POLL_INTERVAL)
            }
        }
    }

    fun onPause() {
        pollJob?.cancel()
        pollJob = null
    }

    private fun CoroutineScope.loadContactsIfMissing() {
        if (contacts.value.isNotEmpty() || contactsJob?.isActive == true) return
        contactsJob = launch {
            contacts.value = unstoppableRepository.supportContacts(swap.filterNotNull().first().subProviderId)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun observeSections() {
        val loadedSwap = swap.filterNotNull()
        val track = loadedSwap.map { it.transactionId }.distinctUntilChanged()
            .flatMapLatest(unstoppableRepository::observeTrack)

        val hidden = balanceHiddenManager.transactionInfoHiddenFlow(transactionUid, walletUid)

        combine(loadedSwap, track, firstRefreshDone, contacts, hidden) { swap, track, refreshed, contacts, hidden ->
            SectionsInput(swap, track, refreshed, contacts, hidden)
        }
            .filter { it.track != null || it.refreshed }
            .map { it.hideAmount to stepsFactory.sections(it.swap, it.track, it.contacts, it.hideAmount) }
            .flowOn(dispatcherProvider.default)
            .collect { (hideAmount, sections) ->
                uiState = UnstoppableSwapProgressUiState(loading = false, hideAmount = hideAmount, sections = sections)
            }
    }

    private suspend fun refresh() {
        val swap = swap.filterNotNull().first()
        loggedOrNull("refresh") {
            if (!swap.isFinished()) {
                updateSwapProviderTransactionsStatusUseCase.updateTransactionStatusByDate(swapDate)
            } else if (unstoppableRepository.observeTrack(swap.transactionId).first()?.isSettled(swap.depositTransactionHash) != true) {
                // Leg hashes and amounts may still arrive after the swap is marked finished.
                unstoppableRepository.track(swap.transactionId, swap.depositTransactionHash)
            }
        }
        firstRefreshDone.value = true
    }

    private suspend fun <T> loggedOrNull(action: String, block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        logger.w(e) { "Failed to $action" }
        null
    }

    private class SectionsInput(
        val swap: SwapProviderTransaction,
        val track: UnstoppableTrack?,
        val refreshed: Boolean,
        val contacts: List<SupportContact>,
        val hideAmount: Boolean,
    )

    internal companion object {
        val POLL_INTERVAL = 15.seconds
    }
}
