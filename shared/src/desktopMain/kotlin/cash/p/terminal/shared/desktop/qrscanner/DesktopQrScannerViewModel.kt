package cash.p.terminal.shared.desktop.qrscanner

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.modules.qrscanner.QrCodeReader
import io.horizontalsystems.core.DispatcherProvider
import java.awt.image.BufferedImage
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface DesktopCameraState {
    data object Starting : DesktopCameraState
    data class Preview(val devices: List<WebcamDevice>, val selected: WebcamDevice) : DesktopCameraState
    data object NoCamera : DesktopCameraState
    data object CameraFailed : DesktopCameraState
}

/** The camera side of the desktop scanner; results go through the common QRScannerViewModel. */
class DesktopQrScannerViewModel(
    private val session: DesktopWebcamSession,
    private val settings: DesktopWebcamSettings,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    private val _cameraState = MutableStateFlow<DesktopCameraState>(DesktopCameraState.Starting)
    val cameraState: StateFlow<DesktopCameraState> = _cameraState.asStateFlow()

    private val _preview = MutableStateFlow<ImageBitmap?>(null)
    val preview: StateFlow<ImageBitmap?> = _preview.asStateFlow()

    private val _scannedText = MutableSharedFlow<String>(
        extraBufferCapacity = SCANNED_TEXT_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val scannedText = _scannedText.asSharedFlow()

    private val latestFrame = AtomicReference<BufferedImage?>(null)
    private var cameraJob: Job? = null

    // Tells apart same-named cameras across pause/resume; the setting keeps only the name.
    private var selectedCameraId: String? = null

    fun start() = startCamera(settings.cameraName, selectedCameraId)

    // The release is queued on the camera thread right now, so it precedes every later open:
    // that queue is the only ordering, whatever the cancelled job is still blocked in.
    fun stop() {
        cameraJob?.cancel()
        session.release()
        // Otherwise the next session could decode this session's last frame before its camera delivers one.
        latestFrame.set(null)
    }

    fun onCameraSelected(device: WebcamDevice) {
        settings.cameraName = device.name
        selectedCameraId = device.id
        startCamera(device.name, device.id)
    }

    override fun onCleared() {
        session.close()
    }

    private fun startCamera(preferredName: String?, preferredId: String?) {
        stop()
        cameraJob = viewModelScope.launch(dispatcherProvider.default) { runCamera(preferredName, preferredId) }
    }

    private suspend fun runCamera(preferredName: String?, preferredId: String?) {
        _cameraState.value = DesktopCameraState.Starting
        try {
            coroutineScope {
                val decoding = launch { decodeLatestFrames() }
                _cameraState.value = previewUntilFailure(session.open(preferredName, preferredId))
                decoding.cancel()
            }
            // On main, where start() and stop() run: a job they cancelled can no longer get here.
            withContext(dispatcherProvider.main) { session.release() }
        } finally {
            _preview.value = null
        }
    }

    private suspend fun previewUntilFailure(firstResult: OpenResult): DesktopCameraState {
        var result = firstResult
        while (result is OpenResult.Opened) {
            _cameraState.value = DesktopCameraState.Preview(result.devices, result.device)
            // Frames that stop mid-preview mean the camera is gone; no first frame means try the next camera.
            if (previewFrames()) return DesktopCameraState.CameraFailed
            result = session.openNext()
        }
        return if (result == OpenResult.NoDevices) DesktopCameraState.NoCamera else DesktopCameraState.CameraFailed
    }

    /** Shows frames until none arrives within [NO_FRAMES_TIMEOUT]; `true` if any frame arrived. */
    @OptIn(FlowPreview::class)
    private suspend fun previewFrames(): Boolean {
        var received = false
        try {
            session.frames().conflate().timeout(NO_FRAMES_TIMEOUT).collect { frame ->
                received = true
                latestFrame.set(frame)
                _preview.value = frame.toComposeImageBitmap()
            }
        } catch (_: TimeoutCancellationException) {
        }
        return received
    }

    private suspend fun decodeLatestFrames() {
        val reader = QrCodeReader()
        while (true) {
            delay(DECODE_INTERVAL_MS)
            val text = latestFrame.getAndSet(null)?.let { reader.decodeFrame(it) } ?: continue
            // Delivered on main, where stop() runs: a decode that outlived its session is dropped there.
            withContext(dispatcherProvider.main) { _scannedText.emit(text) }
        }
    }

    private fun QrCodeReader.decodeFrame(frame: BufferedImage): String? {
        val source = frame.toLuminanceSource()
        return decode(source, cropToCenter = true) ?: decode(source)
    }

    private companion object {
        const val DECODE_INTERVAL_MS = 200L
        const val SCANNED_TEXT_BUFFER = 16
    }
}
