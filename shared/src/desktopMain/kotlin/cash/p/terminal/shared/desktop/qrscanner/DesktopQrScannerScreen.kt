package cash.p.terminal.shared.desktop.qrscanner

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.qrscanner.CameraErrorOverlay
import cash.p.terminal.modules.qrscanner.LoadingOverlay
import cash.p.terminal.modules.qrscanner.QRScannerUiState
import cash.p.terminal.modules.qrscanner.QrScannerButtons
import cash.p.terminal.modules.qrscanner.QrScannerErrorEffect
import cash.p.terminal.modules.qrscanner.ScannerOverlay
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.QrScannerInput
import cash.p.terminal.resources.Button_Retry
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_down_arrow_20
import cash.p.terminal.resources.qr_scanner_no_camera
import cash.p.terminal.resources.qr_scanner_no_camera_hint_linux
import cash.p.terminal.resources.qr_scanner_no_camera_hint_macos
import cash.p.terminal.resources.qr_scanner_no_camera_hint_windows
import cash.p.terminal.resources.qr_scanner_open_image
import cash.p.terminal.resources.qr_scanner_select_camera
import cash.p.terminal.resources.scan_qr_camera_initialization_failed
import cash.p.terminal.ui_compose.components.AppSelectorDialog
import cash.p.terminal.ui_compose.components.AppSelectorItem
import cash.p.terminal.ui_compose.components.ButtonSecondaryDefault
import cash.p.terminal.ui_compose.components.ButtonSecondaryWithIcon
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun DesktopQrScannerScreen(
    uiState: QRScannerUiState,
    cameraState: DesktopCameraState,
    preview: ImageBitmap?,
    input: QrScannerInput,
    navigation: HSNavigation,
    actions: DesktopQrScannerActions,
) {
    QrScannerErrorEffect(uiState.errorMessageRes, onErrorHandle = actions.onErrorMessageConsumed)

    Box(
        Modifier
            .fillMaxSize()
            .background(ComposeAppTheme.colors.scannerBackground),
    ) {
        CameraContent(cameraState, preview, actions.onRetry)

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 50.dp, start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (input.title.isNotEmpty()) {
                Text(
                    text = input.title,
                    textAlign = TextAlign.Center,
                    style = ComposeAppTheme.typography.headline1,
                    color = ComposeAppTheme.colors.contentOnColor,
                )
            }
            if (cameraState is DesktopCameraState.Preview && cameraState.devices.size > 1) {
                CameraSelector(cameraState, actions.onCameraSelected)
            }
        }

        QrScannerButtons(
            navigation = navigation,
            isDecodingFromImage = uiState.isDecodingFromImage,
            showPasteButton = input.showPasteButton,
            galleryTitle = stringResource(Res.string.qr_scanner_open_image),
            onPaste = actions.onPaste,
            onOpenImage = actions.onOpenImage,
            onCloseClick = actions.onClose,
            modifier = Modifier.align(Alignment.BottomCenter),
            allowGalleryWithoutPremium = input.allowGalleryWithoutPremium,
        )

        if (uiState.isDecodingFromImage) {
            LoadingOverlay()
        }
    }
}

internal class DesktopQrScannerActions(
    val onPaste: (String) -> Unit,
    val onOpenImage: () -> Unit,
    val onClose: () -> Unit,
    val onRetry: () -> Unit,
    val onCameraSelected: (WebcamDevice) -> Unit,
    val onErrorMessageConsumed: () -> Unit,
)

@Composable
private fun CameraContent(cameraState: DesktopCameraState, preview: ImageBitmap?, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        when (cameraState) {
            DesktopCameraState.Starting -> LoadingOverlay()
            is DesktopCameraState.Preview -> {
                preview?.let {
                    Image(it, contentDescription = null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
                ScannerOverlay()
            }
            DesktopCameraState.NoCamera -> CameraError(
                message = stringResource(Res.string.qr_scanner_no_camera) + "\n\n" + stringResource(noCameraHint()),
                onRetry = onRetry,
            )
            DesktopCameraState.CameraFailed -> CameraError(
                message = stringResource(Res.string.scan_qr_camera_initialization_failed),
                onRetry = onRetry,
            )
        }
    }
}

@Composable
private fun CameraError(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Shrunk to its text so the retry button sits right below the message.
        CameraErrorOverlay(errorMessage = message, modifier = Modifier.height(IntrinsicSize.Min))
        ButtonSecondaryDefault(title = stringResource(Res.string.Button_Retry), onClick = onRetry)
    }
}

@Composable
private fun CameraSelector(state: DesktopCameraState.Preview, onSelect: (WebcamDevice) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    ButtonSecondaryWithIcon(
        title = state.selected.name,
        iconRight = painterResource(Res.drawable.ic_down_arrow_20),
        onClick = { showDialog = true },
    )
    if (showDialog) {
        AppSelectorDialog(
            title = stringResource(Res.string.qr_scanner_select_camera),
            items = state.devices.map { AppSelectorItem(it.name, it == state.selected, it) },
            onSelect = onSelect,
            onDismiss = { showDialog = false },
        )
    }
}

private fun noCameraHint() = when (DesktopOs.current) {
    DesktopOs.MacOs -> Res.string.qr_scanner_no_camera_hint_macos
    DesktopOs.Windows -> Res.string.qr_scanner_no_camera_hint_windows
    DesktopOs.Linux -> Res.string.qr_scanner_no_camera_hint_linux
}

@Preview
@Composable
private fun DesktopQrScannerScreenPreview() {
    ComposeAppTheme {
        CameraContent(DesktopCameraState.NoCamera, preview = null, onRetry = {})
    }
}
