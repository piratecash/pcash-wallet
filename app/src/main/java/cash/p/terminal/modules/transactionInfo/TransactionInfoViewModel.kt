package cash.p.terminal.modules.transactionInfo

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.R
import cash.p.terminal.core.managers.AddressLabelManager
import cash.p.terminal.core.managers.BeamSendCoordinator.Reason
import cash.p.terminal.core.managers.BeamSendCoordinator.SendException
import cash.p.terminal.core.managers.PendingTransactionRepository
import cash.p.terminal.core.managers.PoisonAddressManager
import cash.p.terminal.entities.transactionrecords.PendingTransactionRecord
import cash.p.terminal.modules.contacts.ContactsRepository
import cash.p.terminal.modules.transactions.addressMetadataChangesFlow
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import cash.p.terminal.wallet.transaction.TransactionSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class TransactionInfoViewModel(
    private val service: TransactionInfoService,
    private val factory: TransactionInfoViewItemFactory,
    private val contactsRepository: ContactsRepository,
    private val balanceHiddenManager: IBalanceHiddenManager,
    private val pendingTransactionRepository: PendingTransactionRepository,
    private val poisonAddressManager: PoisonAddressManager,
    private val addressLabelManager: AddressLabelManager,
) : ViewModel() {

    val balanceHidden: Boolean
        get() = balanceHiddenManager.isTransactionInfoHidden(service.transactionRecord.uid, service.walletUid)

    val source: TransactionSource by service::source
    val transactionRecord by service::transactionRecord

    var viewItems by mutableStateOf<List<List<TransactionInfoViewItem>>>(listOf())
        private set

    /** String resource of the last cancel outcome worth showing; null while there is nothing to show. */
    var beamCancelMessage by mutableStateOf<Int?>(null)
        private set

    init {
        viewModelScope.launch {
            combine(
                service.transactionInfoItemFlow,
                addressMetadataChangesFlow(
                    contactsFlow = contactsRepository.contactsFlow,
                    poisonAddressesChangedFlow = poisonAddressManager.poisonDbChangedFlow,
                    labelsChangedFlow = addressLabelManager.labelsChangedFlow,
                ),
            ) { transactionInfoItem, _ ->
                val updatedItem = transactionInfoItem.copy(
                    poisonStatus = service.computePoisonStatus(transactionInfoItem.record)
                )
                factory.getViewItemSections(updatedItem)
            }.collect { items ->
                viewItems = items
            }
        }

        viewModelScope.launch {
            service.start()
        }
    }

    val isPending: Boolean
        get() = transactionRecord is PendingTransactionRecord

    fun cancelBeamTransaction(transactionHash: String) {
        viewModelScope.launch {
            beamCancelMessage = try {
                if (service.cancelBeam(transactionHash)) null else R.string.beam_cancel_too_late
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                beamCancelErrorMessage(error)
            }
        }
    }

    fun onBeamCancelMessageShown() {
        beamCancelMessage = null
    }

    fun deletePendingTransaction() {
        if (!isPending) return
        viewModelScope.launch {
            pendingTransactionRepository.deleteById(transactionRecord.uid)
        }
    }

    fun getRawTransaction(): String? = service.getRawTransaction()

    fun toggleBalanceVisibility() {
        balanceHiddenManager.toggleTransactionInfoHidden(service.transactionRecord.uid)
    }
}

/** The critical-operation gate reports an unusable BEAM network as [IllegalStateException]. */
@StringRes
private fun beamCancelErrorMessage(error: Exception): Int =
    if (error is IllegalStateException || (error as? SendException)?.reason == Reason.NotReady) {
        R.string.beam_send_not_ready
    } else {
        R.string.beam_wallet_operation_failed
    }
