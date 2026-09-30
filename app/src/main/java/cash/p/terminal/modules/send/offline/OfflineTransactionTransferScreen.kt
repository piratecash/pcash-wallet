package cash.p.terminal.modules.send.offline

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.core.managers.OfflineTransactionPayloadEncoder
import cash.p.terminal.entities.OfflineSignedTransaction
import cash.p.terminal.ui.compose.components.AnimatedQrCode
import cash.p.terminal.ui.compose.components.animatedQrFrames
import cash.p.terminal.ui.compose.components.PcashQrCodeDefaults
import cash.p.terminal.ui.compose.components.PcashQrCodeImage
import cash.p.terminal.ui.compose.components.createPcashQrCodeBitmap
import cash.p.terminal.ui.compose.components.rememberReadablePcashQrCodePainterOrNull
import cash.p.terminal.ui.helpers.TextHelper
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryCircle
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.HudHelper
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.caption_grey
import cash.p.terminal.ui_compose.components.headline2_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.core.DefaultDispatcherProvider
import io.horizontalsystems.core.IPinComponent
import io.horizontalsystems.core.launchExternalActivity
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
internal fun OfflineTransactionTransferScreen(
    transaction: OfflineSignedTransaction?,
    selectedFormat: OfflineTransactionFormat,
    qrCodeSaver: OfflineQrCodeSaver,
    onBackClick: () -> Unit,
    onDoneClick: () -> Unit,
    fileTransfer: OfflineTransactionFileTransfer = koinInject(),
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
) {
    val view = LocalView.current
    val currentOnBackClick by rememberUpdatedState(onBackClick)
    var closingByDone by remember { mutableStateOf(false) }

    LaunchedEffect(transaction, closingByDone) {
        if (transaction == null) {
            if (!closingByDone) {
                HudHelper.showErrorMessage(
                    view,
                    R.string.offline_transaction_signed_transaction_unavailable
                )
                currentOnBackClick()
            }
        }
    }

    Scaffold(
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = {
            AppBar(
                title = stringResource(R.string.offline_transaction_transfer_title),
                navigationIcon = {
                    HsBackButton(onClick = onBackClick)
                },
                menuItems = listOf(),
            )
        },
        bottomBar = {
            if (transaction != null) {
                TransferDoneButton(
                    onDoneClick = {
                        closingByDone = true
                        onDoneClick()
                    },
                    windowInsets = windowInsets,
                )
            }
        },
    ) { paddingValues ->
        when {
            transaction != null -> TransferContent(
                modifier = Modifier.padding(paddingValues),
                transaction = transaction,
                selectedFormat = selectedFormat,
                qrCodeSaver = qrCodeSaver,
                fileTransfer = fileTransfer,
            )
        }
    }
}

@Composable
private fun TransferDoneButton(
    onDoneClick: () -> Unit,
    windowInsets: WindowInsets,
) {
    ButtonPrimaryYellow(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(windowInsets)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        title = stringResource(R.string.Button_Done),
        onClick = onDoneClick,
    )
}

@Composable
private fun TransferContent(
    transaction: OfflineSignedTransaction,
    selectedFormat: OfflineTransactionFormat,
    qrCodeSaver: OfflineQrCodeSaver,
    fileTransfer: OfflineTransactionFileTransfer,
    modifier: Modifier = Modifier,
) {
    val qrContent = selectedFormat.content(transaction)
    val qrCodePainter = rememberReadablePcashQrCodePainterOrNull(qrContent)
    val frames = remember(qrContent) { animatedQrFrames(qrContent) }

    TransferScrollableContent(
        modifier = modifier.fillMaxSize(),
        transaction = transaction,
        selectedFormat = selectedFormat,
        qrContent = qrContent,
        qrCodePainter = qrCodePainter,
        frames = frames,
        qrCodeSaver = qrCodeSaver,
        fileTransfer = fileTransfer,
    )
}

@Composable
private fun TransferScrollableContent(
    transaction: OfflineSignedTransaction,
    selectedFormat: OfflineTransactionFormat,
    qrContent: String,
    qrCodePainter: Painter?,
    frames: List<String>?,
    qrCodeSaver: OfflineQrCodeSaver,
    fileTransfer: OfflineTransactionFileTransfer,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
    ) {
        VSpacer(12.dp)
        TransferHeader(selectedFormat)
        VSpacer(16.dp)
        when {
            qrCodePainter != null -> QrCodePanel {
                QrCodeImage(
                    content = qrContent,
                    qrcodePainter = qrCodePainter,
                )
            }

            frames != null -> QrCodePanel {
                AnimatedQrCode(frames = frames)
            }

            else -> QrCodeUnavailablePanel()
        }
        VSpacer(20.dp)
        TransferActionButtons(
            qrContent = qrContent,
            qrCodePainter = qrCodePainter,
            qrCodeSaver = qrCodeSaver,
            selectedFormat = selectedFormat,
            fileTransfer = fileTransfer,
        )
        VSpacer(24.dp)
        RawTransactionSection(rawHex = transaction.rawHex)
        VSpacer(24.dp)
    }
}

@Composable
private fun TransferHeader(selectedFormat: OfflineTransactionFormat) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        headline2_leah(
            text = stringResource(selectedFormat.transferTitleRes),
        )
        subhead2_grey(
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
            text = stringResource(R.string.offline_transaction_transfer_to_person_description),
        )
    }
}

@Composable
private fun QrCodePanel(
    content: @Composable BoxScope.() -> Unit,
) {
    QrCodePanelFrame(
        content = content,
        footer = {
            VSpacer(12.dp)
            subhead2_grey(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                text = stringResource(R.string.offline_transaction_scan_qr_hint),
                textAlign = TextAlign.Center,
            )
        },
    )
}

@Composable
private fun QrCodeUnavailablePanel() {
    QrCodePanelFrame(
        content = {
            subhead2_grey(
                modifier = Modifier.padding(horizontal = 24.dp),
                text = stringResource(R.string.offline_transaction_qr_too_large),
                textAlign = TextAlign.Center,
            )
        },
    )
}

@Composable
private fun QrCodePanelFrame(
    content: @Composable BoxScope.() -> Unit,
    footer: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(ComposeAppTheme.colors.lawrence)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = PcashQrCodeDefaults.Size)
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(ComposeAppTheme.colors.white),
            contentAlignment = Alignment.Center,
            content = content,
        )
        footer?.invoke()
    }
}

@Composable
private fun QrCodeImage(
    content: String,
    qrcodePainter: Painter,
) {
    PcashQrCodeImage(
        content = content,
        qrCodePainter = qrcodePainter,
    )
}

@Composable
private fun TransferActionButtons(
    qrContent: String,
    qrCodePainter: Painter?,
    qrCodeSaver: OfflineQrCodeSaver,
    selectedFormat: OfflineTransactionFormat,
    fileTransfer: OfflineTransactionFileTransfer,
) {
    val view = LocalView.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (qrCodePainter != null) {
            SaveQrActionButton(
                qrContent = qrContent,
                qrCodePainter = qrCodePainter,
                qrCodeSaver = qrCodeSaver,
                modifier = Modifier.weight(1f),
            )
        }
        TransferActionButton(
            icon = R.drawable.ic_copy_20,
            text = stringResource(R.string.offline_transaction_copy_transaction),
            onClick = {
                TextHelper.copyText(qrContent)
                HudHelper.showSuccessMessage(view, R.string.Hud_Text_Copied)
            },
            modifier = Modifier.weight(1f),
        )
        SaveTransactionFileActionButton(
            content = qrContent,
            selectedFormat = selectedFormat,
            fileTransfer = fileTransfer,
            modifier = Modifier.weight(1f),
        )
        ShareTransactionFileActionButton(
            content = qrContent,
            fileTransfer = fileTransfer,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SaveTransactionFileActionButton(
    content: String,
    selectedFormat: OfflineTransactionFormat,
    fileTransfer: OfflineTransactionFileTransfer,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val pinComponent: IPinComponent = koinInject()
    var saving by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) {
            saving = false
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val saved = fileTransfer.save(context, uri, content)
            if (saved) HudHelper.showSuccessMessage(view, R.string.offline_transaction_file_saved)
            else HudHelper.showErrorMessage(view, R.string.offline_transaction_file_save_failed)
            saving = false
        }
    }

    TransferActionButton(
        icon = R.drawable.ic_download_20,
        text = stringResource(R.string.offline_transaction_save_file),
        onClick = {
            if (saving) return@TransferActionButton
            saving = true
            try {
                pinComponent.launchExternalActivity { launcher.launch(selectedFormat.fileName) }
            } catch (_: ActivityNotFoundException) {
                saving = false
                HudHelper.showErrorMessage(view, R.string.offline_transaction_file_save_failed)
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun ShareTransactionFileActionButton(
    content: String,
    fileTransfer: OfflineTransactionFileTransfer,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val title = stringResource(R.string.Button_Share)
    var sharing by remember { mutableStateOf(false) }

    TransferActionButton(
        icon = R.drawable.ic_share_24px,
        text = title,
        onClick = {
            if (sharing) return@TransferActionButton
            sharing = true
            scope.launch {
                val uri = fileTransfer.createShareUri(context, content)
                if (uri == null) HudHelper.showErrorMessage(view, R.string.offline_transaction_file_save_failed)
                else shareTransactionFile(context, uri, title, view)
                sharing = false
            }
        },
        modifier = modifier,
    )
}

private fun shareTransactionFile(context: Context, uri: Uri, title: String, view: View) {
    try {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(title, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, title))
    } catch (_: RuntimeException) {
        HudHelper.showErrorMessage(view, R.string.offline_transaction_file_save_failed)
    }
}

private val OfflineTransactionFormat.fileName: String
    get() = when (this) {
        OfflineTransactionFormat.Pcash -> "pcash-offline-transaction.txt"
        OfflineTransactionFormat.Raw -> "raw-offline-transaction.txt"
    }

@Composable
private fun SaveQrActionButton(
    qrContent: String,
    qrCodePainter: Painter,
    qrCodeSaver: OfflineQrCodeSaver,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }

    TransferActionButton(
        icon = R.drawable.ic_download_20,
        text = stringResource(R.string.offline_transaction_save_qr),
        onClick = {
            if (isSaving) return@TransferActionButton
            isSaving = true
            scope.launch {
                if (saveQrCode(context, qrContent, qrCodePainter, density, qrCodeSaver)) {
                    HudHelper.showSuccessMessage(view, R.string.offline_transaction_qr_saved)
                } else {
                    HudHelper.showErrorMessage(view, R.string.offline_transaction_qr_save_failed)
                }
                isSaving = false
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun TransferActionButton(
    icon: Int,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ButtonPrimaryCircle(
            icon = icon,
            onClick = onClick,
        )
        caption_grey(
            modifier = Modifier.padding(top = 8.dp),
            text = text,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

private suspend fun saveQrCode(
    context: Context,
    content: String,
    painter: Painter,
    density: Density,
    qrCodeSaver: OfflineQrCodeSaver,
): Boolean =
    try {
        val bitmap = createPcashQrCodeBitmap(
            content = content,
            painter = painter,
            density = density,
        )
        try {
            qrCodeSaver.save(context, bitmap)
        } finally {
            bitmap.recycle()
        }
    } catch (_: Throwable) {
        false
    }

private val OfflineTransactionFormat.transferTitleRes: Int
    get() = when (this) {
        OfflineTransactionFormat.Pcash -> R.string.offline_transaction_pcash_title
        OfflineTransactionFormat.Raw -> R.string.offline_transaction_raw_title
    }

@Suppress("UnusedPrivateMember")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun OfflineTransactionTransferScreenPreview() {
    ComposeAppTheme {
        OfflineTransactionTransferScreen(
            transaction = previewOfflineSignedTransaction,
            selectedFormat = OfflineTransactionFormat.Raw,
            qrCodeSaver = OfflineQrCodeSaver(DefaultDispatcherProvider()),
            onBackClick = {},
            onDoneClick = {},
            fileTransfer = previewFileTransfer,
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
            qrCodeSaver = OfflineQrCodeSaver(DefaultDispatcherProvider()),
            onBackClick = {},
            onDoneClick = {},
            fileTransfer = previewFileTransfer,
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
            qrCodeSaver = OfflineQrCodeSaver(DefaultDispatcherProvider()),
            onBackClick = {},
            onDoneClick = {},
            fileTransfer = previewFileTransfer,
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

private val previewFileTransfer = OfflineTransactionFileTransfer(
    DefaultDispatcherProvider(),
    OfflineTransactionPayloadEncoder(),
)
