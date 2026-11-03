package cash.p.terminal.modules.send.fee

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.ui_compose.components.BottomSheetSurface
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Preview
@Composable
private fun NetworkFeeWarningContentPreview() {
    ComposeAppTheme {
        BottomSheetSurface {
            NetworkFeeWarningContent(
                networkName = "TRON",
                feeAmount = "13.3735 TRX",
                balanceThreshold = "50 TRX",
                feeCoinCode = "TRX",
                onConfirm = {},
                onCancel = {},
            )
        }
    }
}
