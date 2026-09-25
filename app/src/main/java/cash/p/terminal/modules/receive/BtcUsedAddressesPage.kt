package cash.p.terminal.modules.receive

import androidx.compose.runtime.Composable
import cash.p.terminal.modules.receive.ui.UsedAddressScreen
import cash.p.terminal.modules.receive.ui.UsedAddressesParams
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely

class BtcUsedAddressesPage(val input: UsedAddressesParams) : HSPage() {
    @Composable
    override fun GetContent(navigation: HSNavigation) {
        UsedAddressScreen(input) { navigation.navigateUpSafely() }
    }
}
