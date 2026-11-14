package cash.p.terminal.modules.walletconnect.request

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cash.p.terminal.modules.send.signingErrorText
import cash.p.terminal.modules.sendevmtransaction.SectionViewItem
import cash.p.terminal.modules.walletconnect.WCDelegate
import cash.p.terminal.strings.helpers.TranslatableString
import com.reown.walletkit.client.Wallet
import io.horizontalsystems.core.ViewModelUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WCRequestViewModel(
    private val sessionRequest: Wallet.Model.SessionRequest,
    private val wcAction: AbstractWCAction
) : ViewModelUiState<WCRequestUiState>() {

    private var approveInProgress = false
    private var finish: Boolean = false
    private var errorText: String? = null

    private var actionState = wcAction.stateFlow.value

    init {
        viewModelScope.launch {
            wcAction.stateFlow.collect {
                actionState = it

                emitState()
            }
        }

        wcAction.start(viewModelScope)
    }

    override fun createState() = WCRequestUiState(
        title = wcAction.getTitle(),
        finish = finish,
        runnable = !approveInProgress && actionState.runnable,
        approveButtonTitle = wcAction.getApproveButtonTitle(),
        contentItems = actionState.items,
        errorText = errorText
    )

    fun errorShown() {
        errorText = null
        emitState()
    }

    fun approve() = viewModelScope.launch(Dispatchers.Default) {
        errorText = null
        approveInProgress = true
        emitState()

        val actionResult = try {
            wcAction.performAction()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            approveInProgress = false
            showError(e)
            return@launch
        }

        WCDelegate.respondPendingRequest(
            sessionRequest.request.id,
            sessionRequest.topic,
            actionResult,
            onSuccessResult = {
                approveInProgress = false
                finish = true
                emitState()
            },
            onErrorResult = {
                approveInProgress = false
                showError(it)
            }
        )
    }

    fun reject() = viewModelScope.launch(Dispatchers.Default) {
        errorText = null

        WCDelegate.rejectRequest(
            sessionRequest.topic,
            sessionRequest.request.id,
            onSuccessResult = {
                finish = true
                emitState()
            },
            onErrorResult = ::showError
        )

        emitState()
    }

    // A cancellation on the device resets the screen without a message.
    private fun showError(error: Throwable) {
        errorText = error.signingErrorText(error.message ?: error.javaClass.simpleName)
        emitState()
    }

    class Factory(
        private val sessionRequest: Wallet.Model.SessionRequest,
        private val wcAction: AbstractWCAction,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return WCRequestViewModel(sessionRequest, wcAction) as T
        }
    }
}

data class WCRequestUiState(
    val title: TranslatableString,
    val finish: Boolean,
    val runnable: Boolean,
    val approveButtonTitle: TranslatableString,
    val contentItems: List<SectionViewItem>,
    val errorText: String? = null
)
