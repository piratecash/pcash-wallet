package cash.p.terminal.shared.desktop

import cash.p.terminal.core.managers.BalanceHiddenManager
import io.horizontalsystems.core.BackgroundManagerState
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.DesktopSettingsStore
import io.horizontalsystems.core.IBackgroundManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow

internal fun balanceHiddenManager(
    settingsStore: DesktopSettingsStore,
    dispatcherProvider: DispatcherProvider,
) = BalanceHiddenManager(
    localStorage = DesktopWalletSettingsStorage(settingsStore),
    backgroundManager = mockk<IBackgroundManager> {
        every { stateFlow } returns MutableStateFlow(BackgroundManagerState.Unknown)
        every { inForeground } returns true
    },
    dispatcherProvider = dispatcherProvider,
)
