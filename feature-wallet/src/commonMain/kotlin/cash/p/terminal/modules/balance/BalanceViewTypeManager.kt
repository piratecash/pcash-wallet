package cash.p.terminal.modules.balance

import cash.p.terminal.core.AppearanceSettingsStore
import cash.p.terminal.wallet.balance.BalanceViewType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class BalanceViewTypeManager(private val localStorage: AppearanceSettingsStore) {
    val viewTypes = BalanceViewType.entries

    val balanceViewType: BalanceViewType
        get() = localStorage.balanceViewType ?: DEFAULT_VIEW_TYPE

    private val _balanceViewTypeFlow = MutableStateFlow(balanceViewType)

    val balanceViewTypeFlow = _balanceViewTypeFlow.asStateFlow()

    fun setViewType(viewType: BalanceViewType) {
        localStorage.balanceViewType = viewType

        _balanceViewTypeFlow.update {
            viewType
        }
    }

    companion object {
        val DEFAULT_VIEW_TYPE = BalanceViewType.CoinThenFiat
    }
}
