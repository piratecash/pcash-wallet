package cash.p.terminal.ui_compose.components

import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.platform_not_supported_yet
import io.horizontalsystems.core.Translator

/** A null [page] does not exist on this platform yet: the user is told so instead of navigating. */
fun HSNavigation.openOrNotifyUnsupported(page: HSPage?, hud: HudMessenger) {
    if (page == null) {
        hud.showNotSupportedOnPlatform()
    } else {
        slideFromRight(page)
    }
}

fun HudMessenger.showNotSupportedOnPlatform() {
    showWarning(Translator.getString(Res.string.platform_not_supported_yet))
}
