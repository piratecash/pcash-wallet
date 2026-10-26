package cash.p.terminal.core

import io.horizontalsystems.core.CoreApp

class TestCoreApp : CoreApp() {
    override fun localizedContext() = this
    override val isSwapEnabled: Boolean = false
}
