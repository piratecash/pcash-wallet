package cash.p.terminal.modules.multiswap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.multiswap.providers.ProviderRiskType
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Preview
@Composable
private fun ProviderRiskBadgesPreview() {
    ComposeAppTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ProviderRiskType.entries.forEach { type ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EstimationTimeBadge(seconds = 793)
                    ProviderRiskBadge(riskType = type)
                }
            }
        }
    }
}
