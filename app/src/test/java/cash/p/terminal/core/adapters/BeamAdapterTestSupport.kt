package cash.p.terminal.core.adapters

import cash.p.terminal.core.managers.BeamLifecycleCoordinator
import cash.p.terminal.core.managers.BeamSessionOwner
import io.horizontalsystems.core.BackgroundManagerState
import io.horizontalsystems.core.DispatcherProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow

// A foreground, connected, online BEAM adapter whose IO work runs on [dispatcher].
internal fun testBeamAdapter(
    owner: BeamSessionOwner,
    session: BeamSessionOwner.Session,
    dispatcher: CoroutineDispatcher,
): BeamAdapter {
    val dispatchers = mockk<DispatcherProvider> { every { io } returns dispatcher }
    val lifecycle = BeamLifecycleCoordinator(
        mockk { every { stateFlow } returns MutableStateFlow(BackgroundManagerState.EnterForeground) },
        mockk { every { keepAliveBlockchains } returns MutableStateFlow(emptySet()) },
        mockk {
            every { isConnected } returns MutableStateFlow(true)
            every { acquireMonitoringLease() } returns AutoCloseable { }
            coEvery { refreshAndAwaitValidation() } returns true
        },
        mockk {
            every { effectiveFlow } returns MutableStateFlow(emptySet())
            every { stateFlow } returns MutableStateFlow(emptyMap())
            every { isNetworkPaused(any()) } returns false
        },
    )
    return BeamAdapter(owner, session, dispatchers, mockk(relaxed = true), lifecycle)
}
