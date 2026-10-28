package cash.p.terminal.ui.compose.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.qr.multipart.MultipartQrEncoder
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun AnimatedQrCodePreview() {
    val frames = MultipartQrEncoder.encode("ab".repeat(1_000)).orEmpty()
    ComposeAppTheme {
        AnimatedQrCode(frames = frames)
    }
}
