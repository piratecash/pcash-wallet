package cash.p.terminal.shared.desktop.qrscanner

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cash.p.terminal.modules.qrscanner.CollectQrScanResult
import cash.p.terminal.modules.qrscanner.QRScannerViewModel
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.PageResumeEffect
import cash.p.terminal.navigation.QrScannerInput
import cash.p.terminal.navigation.QrScannerResult
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.shared.desktop.chooseFile
import java.awt.FileDialog
import org.koin.compose.viewmodel.koinViewModel

class DesktopQrScannerPage(private val input: QrScannerInput) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val scannerViewModel: QRScannerViewModel = koinViewModel()
        val cameraViewModel: DesktopQrScannerViewModel = koinViewModel()
        val uiState by scannerViewModel.uiState.collectAsStateWithLifecycle()
        val cameraState by cameraViewModel.cameraState.collectAsStateWithLifecycle()
        val preview by cameraViewModel.preview.collectAsStateWithLifecycle()

        CollectQrScanResult(scannerViewModel) { text ->
            navigation.setResult(this, QrScannerResult(text))
            navigation.navigateUp()
        }
        LaunchedEffect(cameraViewModel) {
            cameraViewModel.scannedText.collect(scannerViewModel::onFrameScanned)
        }
        // The camera, and its LED, stay off while the window is minimised.
        PageResumeEffect(onResume = cameraViewModel::start, onPause = cameraViewModel::stop)

        val actions = remember(scannerViewModel, cameraViewModel, navigation) {
            DesktopQrScannerActions(
                onPaste = scannerViewModel::onTextPasted,
                onOpenImage = { chooseFile(FileDialog.LOAD, null)?.let { scannerViewModel.onImagePicked(it.path) } },
                onClose = navigation::navigateUpSafely,
                onRetry = cameraViewModel::start,
                onCameraSelected = cameraViewModel::onCameraSelected,
                onErrorMessageConsumed = scannerViewModel::onErrorMessageConsumed,
            )
        }
        DesktopQrScannerScreen(
            uiState = uiState,
            cameraState = cameraState,
            preview = preview,
            input = input,
            navigation = navigation,
            actions = actions,
        )
    }
}
