package cash.p.terminal.ui.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import java.math.BigDecimal

@Preview
@Composable
private fun BadgePreview() {
    ComposeAppTheme {
        Box(
            modifier = Modifier.padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Badge(text = "#455")
        }
    }
}
@Preview
@Composable
private fun NewDotPreview() {
    ComposeAppTheme {
        Box(
            modifier = Modifier.padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            NewDot()
        }
    }
}
@Preview
@Composable
private fun BadgeCirclePreview() {
    ComposeAppTheme {
        Box(
            modifier = Modifier.padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            BadgeText(
                background = ComposeAppTheme.colors.brandDefault,
                textColor = ComposeAppTheme.colors.backgroundBase,
                text = "1"
            )
        }
    }
}
@Preview
@Composable
private fun BadgeCircleSignalPreview() {
    ComposeAppTheme {
        Box(
            modifier = Modifier.padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            BadgeText(
                background = ComposeAppTheme.colors.statusError20,
                textColor = ComposeAppTheme.colors.statusError,
                text = "Sell"
            )
        }
    }
}
@Preview
@Composable
private fun BadgeWithDiffPreview() {
    ComposeAppTheme {
        Box(
            modifier = Modifier.padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            BadgeWithDiff(text = "35", diff = BigDecimal("5"))
        }
    }
}
@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun BadgeWithDiffOnlyPreview() {
    ComposeAppTheme {
        Box(
            modifier = Modifier.padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            BadgeWithDiff(text = "", diff = BigDecimal("1.63"))
        }
    }
}
