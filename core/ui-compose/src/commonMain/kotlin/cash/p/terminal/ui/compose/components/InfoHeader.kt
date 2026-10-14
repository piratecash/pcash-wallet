package cash.p.terminal.ui.compose.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.components.headline2_brand
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Composable
fun InfoH3(text: String) {
    headline2_brand(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        text = text
    )
}

@Composable
fun InfoH1(text: String) {
    Column(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
    ){
        Text(
            text = text,
            style = ComposeAppTheme.typography.title2,
            color = ComposeAppTheme.colors.textPrimary
        )
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(
            thickness = 1.dp,
            color = ComposeAppTheme.colors.borderDivider
        )
    }
}
