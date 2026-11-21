package cash.p.terminal.core.tor

import io.horizontalsystems.core.IPinComponent

class AndroidTorPlatform(private val pinComponent: IPinComponent) : TorPlatform {
    override val isSupported = true

    override fun beforeRestartAfterDisable() = pinComponent.updateLastExitDateBeforeRestart()
}
