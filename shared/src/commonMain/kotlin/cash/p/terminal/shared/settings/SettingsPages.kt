package cash.p.terminal.shared.settings

import cash.p.terminal.navigation.HSPage

/** The pages the settings pane opens; they live in the platform source set that provides them. */
interface SettingsPages {
    fun security(): HSPage
    fun language(): HSPage
}
