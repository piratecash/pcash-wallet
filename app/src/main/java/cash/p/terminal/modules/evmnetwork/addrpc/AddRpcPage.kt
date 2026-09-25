package cash.p.terminal.modules.evmnetwork.addrpc

import androidx.compose.runtime.Composable
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import io.horizontalsystems.core.entities.Blockchain

class AddRpcPage(val blockchain: Blockchain) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        AddRpcScreen(
            page = this,
            navigation = navigation,
            blockchain = blockchain
        )
    }
}
