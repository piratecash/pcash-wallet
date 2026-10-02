package cash.p.terminal.ui_compose.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.R
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Preview
@Composable
private fun AppSnackbarPreview() = AppSnackbarPreviewContent(darkTheme = false)

@Preview
@Composable
private fun AppSnackbarDarkPreview() = AppSnackbarPreviewContent(darkTheme = true)

@Composable
private fun AppSnackbarPreviewContent(darkTheme: Boolean) {
    ComposeAppTheme(darkTheme = darkTheme) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppSnackbarVariant.entries.forEach { variant ->
                AppSnackbar(
                    text = variant.name,
                    variant = variant,
                    icon = painterResource(R.drawable.ic_attention_24),
                )
            }
        }
    }
}
