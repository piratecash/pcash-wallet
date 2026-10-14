package cash.p.terminal.ui_compose.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Preview(name = "Light", uiMode = Configuration.UI_MODE_NIGHT_NO, widthDp = 360)
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, widthDp = 360)
@Composable
private fun BalanceTypographyPreview() {
    ComposeAppTheme {
        Column(
            modifier = Modifier
                .background(ComposeAppTheme.colors.backgroundBase)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Subhead1("Balance", color = ComposeAppTheme.colors.textPrimary)
            MicroSBSecondary("TOTAL BALANCE")
            Title1Primary("\$1,700.00")
            Title1Primary("\$1,700.00", dimmed = true)
        }
    }
}
