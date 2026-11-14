package cash.p.terminal.modules.xtransaction.sections.ton

import androidx.compose.runtime.Composable
import cash.p.terminal.resources.Transactions_ContractDeploy
import cash.p.terminal.resources.Res
import org.jetbrains.compose.resources.stringResource
import cash.p.terminal.modules.xtransaction.cells.HeaderCell
import cash.p.terminal.ui_compose.components.SectionUniversalLawrence

@Composable
fun ContractDeploySection(
    interfaces: List<String>,
) {
    SectionUniversalLawrence {
        HeaderCell(
            title = stringResource(Res.string.Transactions_ContractDeploy),
            value = interfaces.joinToString(),
            painter = null
        )
    }
}
