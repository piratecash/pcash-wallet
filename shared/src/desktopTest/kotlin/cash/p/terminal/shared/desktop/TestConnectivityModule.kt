package cash.p.terminal.shared.desktop

import cash.p.terminal.manager.IConnectivityManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.dsl.module

/** DesktopNavHost draws the connection panel, which reads the connectivity manager. */
internal val connectedWithoutTorModule = module {
    single<IConnectivityManager> {
        mockk {
            every { torEnabled } returns false
            every { isConnected } returns MutableStateFlow(true)
        }
    }
}
