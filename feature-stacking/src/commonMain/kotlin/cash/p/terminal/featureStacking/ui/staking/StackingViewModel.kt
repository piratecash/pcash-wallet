package cash.p.terminal.featureStacking.ui.staking

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.cosanta
import cash.p.terminal.resources.pirate_cash
import cash.p.terminal.ui_compose.components.TabItem
import io.horizontalsystems.core.Translator

internal class StackingViewModel(initialType: StackingType?) : ViewModel() {

    private val _uiState =
        mutableStateOf(
            StackingUIState(
                tabs = listOf(
                    TabItem(
                        title = Translator.getString(Res.string.pirate_cash),
                        selected = true,
                        item = StackingType.PCASH
                    ),
                    TabItem(
                        title = Translator.getString(Res.string.cosanta),
                        selected = false,
                        item = StackingType.COSANTA
                    )
                )
            )
        )
    val uiState: State<StackingUIState> get() = _uiState

    // Applied before the first composition so the pager starts on the requested tab.
    init {
        initialType?.let(::setStackingType)
    }

    fun setStackingType(stackingType: StackingType) {
        _uiState.value = uiState.value.copy(
            tabs = uiState.value.tabs.map {
                it.copy(selected = it.item == stackingType)
            }
        )
    }
}
