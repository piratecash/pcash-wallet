@file:Suppress("PackageNaming")

package cash.p.terminal.modules.transactions.poison_status

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Suppress("UnusedPrivateMember")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PoisonStatusBadgePreview() {
    ComposeAppTheme {
        Column {
            PoisonStatus.entries.forEach { status ->
                PoisonStatusBadge(
                    poisonStatus = status,
                    onInfoClick = {},
                )
                Spacer(modifier = Modifier.size(8.dp))
            }
        }
    }
}
