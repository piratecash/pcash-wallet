package cash.p.terminal.ui_compose.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Preview(name = "Light", uiMode = Configuration.UI_MODE_NIGHT_NO, widthDp = 360)
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, widthDp = 360)
@Composable
private fun HsSwitchPreview() {
    ComposeAppTheme {
        Row(
            modifier = Modifier
                .background(ComposeAppTheme.colors.backgroundBase)
                .padding(16.dp)
        ) {
            HsSwitch(checked = true, onCheckedChange = {}, modifier = Modifier.size(width = 44.dp, height = 24.dp))
            HsSwitch(checked = false, onCheckedChange = {}, modifier = Modifier.size(width = 44.dp, height = 24.dp))
            HsSwitch(
                checked = true,
                onCheckedChange = {},
                enabled = false,
                modifier = Modifier.size(width = 44.dp, height = 24.dp)
            )
            HsSwitch(
                checked = false,
                onCheckedChange = {},
                enabled = false,
                modifier = Modifier.size(width = 44.dp, height = 24.dp)
            )
        }
    }
}
