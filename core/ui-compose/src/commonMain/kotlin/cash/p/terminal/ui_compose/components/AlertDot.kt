package cash.p.terminal.ui_compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Composable
fun AlertDot(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(8.dp)
            .background(ComposeAppTheme.colors.statusError, shape = CircleShape)
    )
}

@Preview
@Composable
private fun AlertDotPreview() {
    ComposeAppTheme {
        AlertDot()
    }
}
