package cash.p.terminal.feature.logging.settings

import cash.p.terminal.premium.domain.usecase.CheckPremiumUseCase
import cash.p.terminal.premium.domain.usecase.PremiumType
import io.horizontalsystems.core.ILoggingSettings
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class LoggingSettingsViewModelTest {
    // Every setter is a no-op and every getter says off: a desktop setter that refused the change.
    private val refusingSettings = mockk<ILoggingSettings>(relaxed = true)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun setters_settingRefusesToEnable_switchStaysOff() {
        val viewModel = LoggingSettingsViewModel(
            loggingSettings = refusingSettings,
            loginRecordRepository = mockk(relaxed = true) { every { observeHasRecords(any()) } returns emptyFlow() },
            userManager = mockk { every { getUserLevel() } returns 0 },
            journalSecrets = mockk(relaxed = true) { every { changes } returns emptyFlow() },
            checkPremiumUseCase = mockk<CheckPremiumUseCase> { every { getPremiumType() } returns PremiumType.PIRATE },
        )

        viewModel.setLogUnsuccessfulLoginsEnabled(true)
        viewModel.setLogIntoDuressModeEnabled(true)

        assertFalse(viewModel.uiState.logUnsuccessfulLoginsEnabled)
        assertFalse(viewModel.uiState.logIntoDuressModeEnabled)
    }
}
