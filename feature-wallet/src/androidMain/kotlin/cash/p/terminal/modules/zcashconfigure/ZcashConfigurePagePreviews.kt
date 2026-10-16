package cash.p.terminal.modules.zcashconfigure

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Preview
@Composable
private fun Preview_ZcashConfigure() {
    ComposeAppTheme(darkTheme = false) {
        ZcashConfigureScreen(
            onCloseClick = {},
            onCloseWithResult = {},
            initialConfig = null
        )
    }
}
