package cash.p.terminal.modules.receive

import androidx.compose.runtime.Composable
import cash.p.terminal.modules.receive.ui.MoneroUsedAddressesScreen
import cash.p.terminal.modules.receive.viewmodels.MoneroUsedAddressesParams
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely

class MoneroUsedAddressesPage(val input: MoneroUsedAddressesParams) : HSPage() {
    @Composable
    override fun GetContent(navigation: HSNavigation) {
        MoneroUsedAddressesScreen(
            params = input,
            onBackPress = navigation::navigateUpSafely
        )
    }
}
