package cash.p.terminal.modules.send.offline

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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import cash.p.terminal.core.IClipboardManager
import cash.p.terminal.entities.OfflineSignedTransaction
import cash.p.terminal.resources.Button_Done
import cash.p.terminal.resources.Button_Share
import cash.p.terminal.resources.Hud_Text_Copied
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_copy_20
import cash.p.terminal.resources.ic_download_20
import cash.p.terminal.resources.ic_share_24px
import cash.p.terminal.resources.offline_transaction_copy_transaction
import cash.p.terminal.resources.offline_transaction_file_save_failed
import cash.p.terminal.resources.offline_transaction_file_saved
import cash.p.terminal.resources.offline_transaction_pcash_title
import cash.p.terminal.resources.offline_transaction_qr_save_failed
import cash.p.terminal.resources.offline_transaction_qr_saved
import cash.p.terminal.resources.offline_transaction_qr_too_large
import cash.p.terminal.resources.offline_transaction_raw_title
import cash.p.terminal.resources.offline_transaction_save_file
import cash.p.terminal.resources.offline_transaction_save_qr
import cash.p.terminal.resources.offline_transaction_scan_qr_hint
import cash.p.terminal.resources.offline_transaction_signed_transaction_unavailable
import cash.p.terminal.resources.offline_transaction_transfer_title
import cash.p.terminal.resources.offline_transaction_transfer_to_person_description
import cash.p.terminal.ui.compose.components.AnimatedQrCode
import cash.p.terminal.ui.compose.components.animatedQrFrames
import cash.p.terminal.ui.compose.components.PcashQrCodeDefaults
import cash.p.terminal.ui.compose.components.PcashQrCodeImage
import cash.p.terminal.ui.compose.components.createPcashQrCodeBitmap
import cash.p.terminal.ui.compose.components.rememberReadablePcashQrCodePainterOrNull
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryCircle
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.HudMessenger
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.caption_grey
import cash.p.terminal.ui_compose.components.headline2_leah
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.core.Translator
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
internal fun OfflineTransactionTransferScreen(
    transaction: OfflineSignedTransaction?,
    selectedFormat: OfflineTransactionFormat,
    onBackClick: () -> Unit,
    onDoneClick: () -> Unit,
    files: OfflineTransactionFiles = koinInject(),
    qrImageSaver: OfflineQrImageSaver = koinInject(),
    clipboardManager: IClipboardManager = koinInject(),
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
) {
    val hud = rememberHudMessenger()
    val currentOnBackClick by rememberUpdatedState(onBackClick)
    var closingByDone by remember { mutableStateOf(false) }

    LaunchedEffect(transaction, closingByDone) {
        if (transaction == null) {
            if (!closingByDone) {
                hud.showError(Translator.getString(Res.string.offline_transaction_signed_transaction_unavailable))
                currentOnBackClick()
            }
        }
    }

    Scaffold(
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = {
            AppBar(
                title = stringResource(Res.string.offline_transaction_transfer_title),
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
                files = files,
                qrImageSaver = qrImageSaver,
                clipboardManager = clipboardManager,
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
        title = stringResource(Res.string.Button_Done),
        onClick = onDoneClick,
    )
}

@Composable
private fun TransferContent(
    transaction: OfflineSignedTransaction,
    selectedFormat: OfflineTransactionFormat,
    files: OfflineTransactionFiles,
    qrImageSaver: OfflineQrImageSaver,
    clipboardManager: IClipboardManager,
    modifier: Modifier = Modifier,
) {
    val qrContent = selectedFormat.content(transaction)
    val qrCodePainter = rememberReadablePcashQrCodePainterOrNull(qrContent)
    val frames = remember(qrContent) { animatedQrFrames(qrContent) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        VSpacer(12.dp)
        TransferHeader(selectedFormat)
        VSpacer(16.dp)
        when {
            qrCodePainter != null -> QrCodePanel {
                PcashQrCodeImage(
                    content = qrContent,
                    qrCodePainter = qrCodePainter,
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
            qrImageSaver = qrImageSaver,
            selectedFormat = selectedFormat,
            files = files,
            clipboardManager = clipboardManager,
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        headline2_leah(
            text = stringResource(selectedFormat.transferTitleRes),
        )
        subhead2_grey(
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
            text = stringResource(Res.string.offline_transaction_transfer_to_person_description),
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
                text = stringResource(Res.string.offline_transaction_scan_qr_hint),
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
                text = stringResource(Res.string.offline_transaction_qr_too_large),
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
            .background(ComposeAppTheme.colors.surfacePrimary)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = PcashQrCodeDefaults.Size)
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(ComposeAppTheme.colors.qrBackground),
            contentAlignment = Alignment.Center,
            content = content,
        )
        footer?.invoke()
    }
}

@Composable
private fun TransferActionButtons(
    qrContent: String,
    qrCodePainter: Painter?,
    qrImageSaver: OfflineQrImageSaver,
    selectedFormat: OfflineTransactionFormat,
    files: OfflineTransactionFiles,
    clipboardManager: IClipboardManager,
) {
    val hud = rememberHudMessenger()

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
                qrImageSaver = qrImageSaver,
                modifier = Modifier.weight(1f),
            )
        }
        TransferActionButton(
            icon = Res.drawable.ic_copy_20,
            text = stringResource(Res.string.offline_transaction_copy_transaction),
            onClick = {
                clipboardManager.copyText(qrContent)
                hud.showSuccess(Translator.getString(Res.string.Hud_Text_Copied))
            },
            modifier = Modifier.weight(1f),
        )
        SaveTransactionFileActionButton(
            content = qrContent,
            selectedFormat = selectedFormat,
            files = files,
            modifier = Modifier.weight(1f),
        )
        ShareTransactionFileActionButton(
            content = qrContent,
            files = files,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SaveTransactionFileActionButton(
    content: String,
    selectedFormat: OfflineTransactionFormat,
    files: OfflineTransactionFiles,
    modifier: Modifier = Modifier,
) {
    val hud = rememberHudMessenger()
    var saving by remember { mutableStateOf(false) }
    val saveFile = files.rememberSaveFile(content) { result ->
        hud.showSaveResult(
            result,
            Res.string.offline_transaction_file_saved,
            Res.string.offline_transaction_file_save_failed
        )
        saving = false
    }

    TransferActionButton(
        icon = Res.drawable.ic_download_20,
        text = stringResource(Res.string.offline_transaction_save_file),
        onClick = {
            if (saving) return@TransferActionButton
            saving = true
            saveFile(selectedFormat.fileName)
        },
        modifier = modifier,
    )
}

@Composable
private fun ShareTransactionFileActionButton(
    content: String,
    files: OfflineTransactionFiles,
    modifier: Modifier = Modifier,
) {
    val hud = rememberHudMessenger()
    var sharing by remember { mutableStateOf(false) }
    val shareFile = files.rememberShareFile(content) { shared ->
        if (!shared) hud.showError(Translator.getString(Res.string.offline_transaction_file_save_failed))
        sharing = false
    } ?: return

    TransferActionButton(
        icon = Res.drawable.ic_share_24px,
        text = stringResource(Res.string.Button_Share),
        onClick = {
            if (sharing) return@TransferActionButton
            sharing = true
            shareFile()
        },
        modifier = modifier,
    )
}

private fun HudMessenger.showSaveResult(
    result: OfflineFileSaveResult,
    savedText: StringResource,
    failedText: StringResource,
) {
    when (result) {
        OfflineFileSaveResult.Saved -> showSuccess(Translator.getString(savedText))
        OfflineFileSaveResult.Failed -> showError(Translator.getString(failedText))
        OfflineFileSaveResult.Cancelled -> Unit
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
    qrImageSaver: OfflineQrImageSaver,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val hud = rememberHudMessenger()
    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }

    TransferActionButton(
        icon = Res.drawable.ic_download_20,
        text = stringResource(Res.string.offline_transaction_save_qr),
        onClick = {
            if (isSaving) return@TransferActionButton
            isSaving = true
            scope.launch {
                hud.showSaveResult(
                    saveQrCode(qrContent, qrCodePainter, density, qrImageSaver),
                    Res.string.offline_transaction_qr_saved,
                    Res.string.offline_transaction_qr_save_failed
                )
                isSaving = false
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun TransferActionButton(
    icon: DrawableResource,
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
    content: String,
    painter: Painter,
    density: Density,
    qrImageSaver: OfflineQrImageSaver,
): OfflineFileSaveResult =
    try {
        qrImageSaver.save(createPcashQrCodeBitmap(content, painter, density))
    } catch (_: Throwable) {
        OfflineFileSaveResult.Failed
    }

private val OfflineTransactionFormat.transferTitleRes: StringResource
    get() = when (this) {
        OfflineTransactionFormat.Pcash -> Res.string.offline_transaction_pcash_title
        OfflineTransactionFormat.Raw -> Res.string.offline_transaction_raw_title
    }
