package cash.p.terminal.modules.send.offline

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.core.IClipboardManager
import cash.p.terminal.entities.OfflineSignedTransaction
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Suppress("UnusedPrivateMember")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun OfflineTransactionTransferScreenPreview() {
    ComposeAppTheme {
        OfflineTransactionTransferScreen(
            transaction = previewOfflineSignedTransaction,
            selectedFormat = OfflineTransactionFormat.Raw,
            onBackClick = {},
            onDoneClick = {},
            files = PreviewOfflineSeams,
            qrImageSaver = PreviewOfflineSeams,
            clipboardManager = PreviewOfflineSeams,
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun OfflineTransactionTransferScreenAnimatedPreview() {
    ComposeAppTheme {
        OfflineTransactionTransferScreen(
            transaction = previewOfflineSignedTransaction.copy(rawHex = "ab".repeat(1_000)),
            selectedFormat = OfflineTransactionFormat.Raw,
            onBackClick = {},
            onDoneClick = {},
            files = PreviewOfflineSeams,
            qrImageSaver = PreviewOfflineSeams,
            clipboardManager = PreviewOfflineSeams,
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun OfflineTransactionTransferScreenQrUnavailablePreview() {
    ComposeAppTheme {
        OfflineTransactionTransferScreen(
            transaction = previewOfflineSignedTransaction.copy(rawHex = "ab".repeat(100_000)),
            selectedFormat = OfflineTransactionFormat.Raw,
            onBackClick = {},
            onDoneClick = {},
            files = PreviewOfflineSeams,
            qrImageSaver = PreviewOfflineSeams,
            clipboardManager = PreviewOfflineSeams,
        )
    }
}

private val previewOfflineSignedTransaction = OfflineSignedTransaction(
    rawHex = "02000000000101d6a5b0c8b3b0cc8f0a08b6f4a6b9c8f1e2d3c4b5a69788776655443322110000" +
            "000000ffffffff02d00700000000000016001489abcdefabbaabbaabbaabbaabbaabbaabbaabba" +
            "8b00000000000000160014abcdef89abbaabbaabbaabbaabbaabbaabbaabba0247304402201f" +
            "2d3c4b5a697887766554433221100ffeeddccbbaa9988776655443322110002206f5e4d3c2b" +
            "1a0099887766554433221100ffeeddccbbaa99887766554433221100012102abcdefabcdefab" +
            "cdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdef00000000",
    pcashPayload = "pcash:tx:v1:bitcoin:eNqLrlZKSSxJVLJSykzOSC1KVrJS8kvMTVWyMjQwMFIyNjE1NLO0BAA0QQmG",
    txHash = "7c2a4ef0a8823a1d90f5d557b1c75675e5efb3d8802aa1d4e58c1cc3d3a3f8f2",
    createdAt = 0L,
)

private object PreviewOfflineSeams : OfflineTransactionFiles, OfflineQrImageSaver, IClipboardManager {
    override suspend fun read(location: String) = OfflineTransactionFileTransfer.ReadResult.Unavailable

    @Composable
    override fun rememberOpenFile(onOpen: (location: String) -> Unit, onError: () -> Unit): () -> Unit = {}

    @Composable
    override fun rememberSaveFile(content: String, onResult: (OfflineFileSaveResult) -> Unit): (String) -> Unit = {}

    @Composable
    override fun rememberShareFile(content: String, onResult: (shared: Boolean) -> Unit): () -> Unit = {}

    override suspend fun save(image: ImageBitmap) = OfflineFileSaveResult.Failed
    override fun copyText(text: String) = Unit
    override fun getCopiedText() = ""
    override val hasPrimaryClip = false
}
