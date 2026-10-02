package cash.p.terminal.ui_compose.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Composable
fun HsRadioButton(
    modifier: Modifier = Modifier,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    HsIconButton(
        modifier = modifier,
        onClick = onClick,
        enabled = enabled,
    ) {
        SelectionIndicator(selected = selected, enabled = enabled)
    }
}

@Preview(name = "Light", uiMode = Configuration.UI_MODE_NIGHT_NO, widthDp = 360)
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, widthDp = 360)
@Composable
private fun HsRadioButtonPreview() {
    ComposeAppTheme {
        Row(
            modifier = Modifier
                .background(ComposeAppTheme.colors.backgroundBase)
                .padding(16.dp)
        ) {
            HsRadioButton(selected = false, onClick = {})
            HsRadioButton(selected = true, onClick = {})
            HsRadioButton(selected = true, onClick = {}, enabled = false)
        }
    }
}
