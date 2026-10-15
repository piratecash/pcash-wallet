package cash.p.terminal.shared.desktop.settings

import cash.p.terminal.navigation.HSPage
import cash.p.terminal.shared.settings.SettingsPages

class DesktopSettingsPages : SettingsPages {
    override fun security(): HSPage = DesktopSecurityPage()
    override fun language(): HSPage = DesktopLanguageSettingsPage()
}
