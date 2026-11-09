package cash.p.terminal.modules.tor

import cash.p.terminal.core.ITorManager
import cash.p.terminal.core.managers.ConnectivityManager
import cash.p.terminal.modules.settings.security.tor.TorStatus
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TorConnectionStatusUseCaseTest {

    private val status = MutableStateFlow(TorStatus.Connecting)
    private val progress = MutableStateFlow<Int?>(null)
    private val torManager = mockk<ITorManager> {
        every { torStatusFlow } returns status
        every { bootstrapProgressFlow } returns progress
    }
    private val connectivityManager = mockk<ConnectivityManager> {
        every { isConnected } returns MutableStateFlow(true)
    }
    private val useCase = TorConnectionStatusUseCase(torManager, connectivityManager)

    @Test
    fun torViewState_bootstrapping_exposesProgress() = runTest {
        progress.value = 45

        assertEquals(45, useCase.torViewState.first().bootstrapProgress)
    }

    @Test
    fun torViewState_noBootstrap_progressIsNull() = runTest {
        assertNull(useCase.torViewState.first().bootstrapProgress)
    }

    @Test
    fun torViewState_progressChanges_emitsUpdatedStates() = runTest {
        progress.value = 10
        val first = useCase.torViewState.first()
        progress.value = 70
        val second = useCase.torViewState.first()

        assertEquals(listOf(10, 70), listOf(first.bootstrapProgress, second.bootstrapProgress))
    }
}
