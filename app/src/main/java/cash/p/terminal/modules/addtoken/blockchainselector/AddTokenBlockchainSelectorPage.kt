package cash.p.terminal.modules.addtoken.blockchainselector

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.SectionUniversalLawrence
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.chartview.cell.CellBlockchainChecked
import io.horizontalsystems.core.entities.Blockchain

class AddTokenBlockchainSelectorPage(
    private val blockchains: List<Blockchain>,
    private val selectedBlockchain: Blockchain,
) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        AddTokenBlockchainSelectorScreen(
            blockchains = blockchains,
            selectedBlockchain = selectedBlockchain,
            onSelect = { blockchain ->
                navigation.setResult(this, Result(blockchain))
                navigation.navigateUpSafely()
            },
            onBack = navigation::navigateUpSafely,
        )
    }

    data class Result(val blockchain: Blockchain)
}

@Composable
private fun AddTokenBlockchainSelectorScreen(
    blockchains: List<Blockchain>,
    selectedBlockchain: Blockchain,
    onSelect: (Blockchain) -> Unit,
    onBack: () -> Unit,
) {
    var selectedItem = selectedBlockchain

    Scaffold(
        topBar = {
            AppBar(
                title = stringResource(R.string.Market_Filter_Blockchains),
                navigationIcon = {
                    HsBackButton(onClick = onBack)
                },
            )
        },
        containerColor = ComposeAppTheme.colors.tyler
    ) {
        Column(
            modifier = Modifier
                .padding(it)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(12.dp))
            SectionUniversalLawrence {
                blockchains.forEachIndexed { index, item ->
                    CellBlockchainChecked(
                        borderTop = index != 0,
                        blockchain = item,
                        checked = selectedItem == item,
                    ) {
                        selectedItem = item
                        onSelect(item)
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
