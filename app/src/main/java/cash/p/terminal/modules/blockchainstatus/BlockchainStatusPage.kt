package cash.p.terminal.modules.blockchainstatus

import androidx.compose.runtime.Composable
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import io.horizontalsystems.core.entities.Blockchain
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class BlockchainStatusPage(val input: Blockchain) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val provider = rememberBlockchainStatusProvider(input)
        val viewModel = koinViewModel<BlockchainStatusViewModel> {
            parametersOf(provider)
        }
        BlockchainStatusScreen(
            viewModel = viewModel,
            onBack = navigation::navigateUpSafely
        )
    }
}
