package cash.p.terminal.modules.contacts

import androidx.compose.runtime.Composable
import cash.p.terminal.navigation.HSNavigation

/** Settings of the PIN that guards deleting all contacts; desktop has no app PIN and shows nothing. */
interface DeleteContactsPinSettings {
    @Composable
    fun Section(navigation: HSNavigation)
}
