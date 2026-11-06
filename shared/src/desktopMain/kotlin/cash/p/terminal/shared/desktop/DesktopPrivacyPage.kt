package cash.p.terminal.shared.desktop

import androidx.compose.runtime.Composable
import cash.p.terminal.modules.settings.privacy.PrivacyScreen
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely

class DesktopPrivacyPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        PrivacyScreen(
            shareCrashData = null,
            onToggleShareCrashData = {},
            onClose = navigation::navigateUpSafely
        )
    }
}
