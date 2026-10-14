package cash.p.terminal.shared.desktop

import cash.p.terminal.core.tryOrNull
import cash.p.terminal.ui_compose.components.UrlOpener
import co.touchlab.kermit.Logger
import java.awt.Desktop
import java.net.URI

class DesktopUrlOpener : UrlOpener {
    private val logger = Logger.withTag("DesktopUrlOpener")

    override fun open(url: String) {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            logger.w { "Opening a browser is not supported on this platform" }
            return
        }
        tryOrNull { Desktop.getDesktop().browse(URI(url)) } ?: logger.w { "Failed to open a browser" }
    }
}
