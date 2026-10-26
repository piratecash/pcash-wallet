package cash.p.terminal.featureStacking.ui.staking

import org.junit.Assert.assertEquals
import org.junit.Test

class StackingViewModelTest {

    @Test
    fun init_cosantaRequested_firstStateSelectsCosanta() {
        assertEquals(StackingType.COSANTA, StackingViewModel(StackingType.COSANTA).selectedType())
    }

    @Test
    fun init_noTypeRequested_firstStateSelectsPirateCash() {
        assertEquals(StackingType.PCASH, StackingViewModel(null).selectedType())
    }

    private fun StackingViewModel.selectedType() = uiState.value.tabs.single { it.selected }.item
}
