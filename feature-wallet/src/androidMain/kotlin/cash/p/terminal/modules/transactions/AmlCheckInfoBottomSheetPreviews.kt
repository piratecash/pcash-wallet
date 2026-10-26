package cash.p.terminal.modules.transactions

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Composable
private fun AmlCheckInfoBottomSheetPreview() {
    ComposeAppTheme {
        AmlCheckInfoBottomSheet(
            onPremiumSettingsClick = {},
            onLaterClick = {},
            onDismiss = {}
        )
    }
}
