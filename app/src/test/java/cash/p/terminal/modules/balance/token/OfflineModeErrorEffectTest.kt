package cash.p.terminal.modules.balance.token

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import cash.p.terminal.modules.offline.OfflineModeToggleUiState
import cash.p.terminal.modules.offline.OfflineModeToggleViewModel
import cash.p.terminal.ui_compose.components.HudHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class OfflineModeErrorEffectTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val state = mutableStateOf(OfflineModeToggleUiState())
    private val viewModel = mockk<OfflineModeToggleViewModel> {
        every { uiState } answers { state.value }
        every { errorShown() } answers { state.value = state.value.copy(error = null) }
    }

    @Before
    fun setUp() {
        mockkObject(HudHelper)
        every { HudHelper.showErrorMessage(any(), any<String>(), any()) } returns null
    }

    @After
    fun tearDown() {
        unmockkObject(HudHelper)
    }

    // Both TokenBalance and AssetSettings are composed while the push/pop transition runs.
    @Test
    fun offlineModeErrorEffect_bothPagesComposed_showsErrorOnce() {
        composeRule.setContent {
            OfflineModeErrorEffect(viewModel)
            OfflineModeErrorEffect(viewModel)
        }

        composeRule.runOnIdle { state.value = state.value.copy(error = ERROR) }
        composeRule.waitForIdle()

        verify(exactly = 1) { HudHelper.showErrorMessage(any(), ERROR, any()) }
        verify(exactly = 1) { viewModel.errorShown() }
    }

    private companion object {
        const val ERROR = "Failed to go online"
    }
}
