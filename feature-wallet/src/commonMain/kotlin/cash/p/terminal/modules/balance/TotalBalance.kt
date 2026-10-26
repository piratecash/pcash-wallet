package cash.p.terminal.modules.balance

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.core.managers.BalanceHiddenManager
import io.horizontalsystems.core.IAppNumberFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch


interface ITotalBalance {
    val balanceHidden: Boolean
    val totalUiState: TotalUIState
    val stateFlow: StateFlow<TotalService.State>
    val balanceHiddenFlow: StateFlow<Boolean>

    fun toggleBalanceVisibility()
    fun toggleTotalType()
}

class TotalBalance(
    private val totalService: TotalService,
    private val balanceHiddenManager: BalanceHiddenManager,
) : ITotalBalance {

    private val numberFormatter: IAppNumberFormatter = getKoinInstance()

    private var totalState = totalService.stateFlow.value

    override val balanceHidden by balanceHiddenManager::balanceHidden
    override val balanceHiddenFlow = balanceHiddenManager.balanceHiddenFlow

    override var totalUiState by mutableStateOf(createTotalUIState())
        private set

    override val stateFlow: StateFlow<TotalService.State>
        get() = totalService.stateFlow

    fun start(viewModelScope: CoroutineScope) {
        viewModelScope.launch {
            totalService.stateFlow.collect {
                totalState = it
                totalUiState = createTotalUIState()
            }
        }
        totalService.start()
    }

    private fun createTotalUIState() = when (val state = totalState) {
        TotalService.State.Hidden -> TotalUIState.Hidden
        is TotalService.State.Visible -> TotalUIState.Visible(
            primaryAmountStr = getPrimaryAmount(state) ?: "---",
            secondaryAmountStr = getSecondaryAmount(state) ?: "---",
            dimmed = state.dimmed
        )
    }

    fun stop() {
        totalService.stop()
    }

    fun setTotalServiceItems(map: List<TotalService.BalanceItem>?) {
        totalService.setItems(map)
    }

    override fun toggleBalanceVisibility() {
        balanceHiddenManager.toggleBalanceHidden()
    }

    override fun toggleTotalType() {
        totalService.toggleType()
    }

    private fun getPrimaryAmount(
        totalState: TotalService.State.Visible
    ) = totalState.currencyValue?.let {
        numberFormatter.formatFiatShort(it.value, it.currency.symbol, 2)
    }

    private fun getSecondaryAmount(
        totalState: TotalService.State.Visible
    ) = totalState.coinValue?.let {
        "~" + numberFormatter.formatCoinFull(it.value, it.coin.code, it.decimal)
    }
}
