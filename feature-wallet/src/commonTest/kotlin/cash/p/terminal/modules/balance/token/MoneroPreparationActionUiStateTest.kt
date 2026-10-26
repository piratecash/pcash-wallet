package cash.p.terminal.modules.balance.token

import cash.p.terminal.resources.Button_Retry
import cash.p.terminal.resources.Res
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneroPreparationActionUiStateTest {

    @Test
    fun actionUiState_completedWithoutError_exposesRetryWithoutLoading() {
        val state = moneroPreparationActionUiState(
            syncInProgress = false,
        )

        assertEquals(Res.string.Button_Retry, state.title)
        assertTrue(state.enabled)
        assertFalse(state.loading)
    }
}
