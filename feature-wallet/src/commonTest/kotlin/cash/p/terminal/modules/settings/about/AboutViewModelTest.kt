package cash.p.terminal.modules.settings.about

import cash.p.terminal.core.ITermsManager
import io.horizontalsystems.core.ISystemInfoManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AboutViewModelTest {

    private val termsAccepted = MutableSharedFlow<Boolean>()
    private val termsManager = mockk<ITermsManager> {
        every { allTermsAccepted } returns false
        every { termsAcceptedSignalFlow } returns termsAccepted
    }
    private val systemInfoManager = mockk<ISystemInfoManager> {
        every { appVersionDisplay } returns "1.2.3"
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_termsNotAccepted_showsAlertAndVersion() {
        val viewModel = AboutViewModel(termsManager, systemInfoManager)

        assertTrue(viewModel.uiState.termsShowAlert)
        assertEquals("1.2.3", viewModel.uiState.appVersion)
    }

    @Test
    fun termsAcceptedSignal_allAccepted_hidesAlert() = runTest {
        val viewModel = AboutViewModel(termsManager, systemInfoManager)

        termsAccepted.emit(true)

        assertFalse(viewModel.uiState.termsShowAlert)
    }

    @Test
    fun termsAcceptedSignal_notAllAccepted_showsAlert() = runTest {
        every { termsManager.allTermsAccepted } returns true
        val viewModel = AboutViewModel(termsManager, systemInfoManager)
        assertFalse(viewModel.uiState.termsShowAlert)

        termsAccepted.emit(false)

        assertTrue(viewModel.uiState.termsShowAlert)
    }
}
