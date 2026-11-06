package cash.p.terminal.modules.qrscanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.qr.multipart.FeedResult
import cash.p.terminal.qr.multipart.MultipartQrScanSession
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.image_decode_failed
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

class QRScannerViewModel(
    private val qrCodeImageDecoder: QrCodeImageDecoder,
) : ViewModel() {

    private val _uiState = MutableStateFlow(QRScannerUiState())
    val uiState = _uiState.asStateFlow()

    private val _scanResult = MutableSharedFlow<String>(replay = 1)
    val scanResult = _scanResult.asSharedFlow()

    private val scanSession = MultipartQrScanSession()
    private val resultEmitted = AtomicBoolean(false)

    fun onFrameScanned(frame: String) {
        when (val result = scanSession.feed(frame)) {
            is FeedResult.NotMultipart -> emitOnce(result.raw)
            is FeedResult.Complete -> emitOnce(result.text)
            is FeedResult.Incomplete -> Unit
        }
    }

    fun onTextPasted(text: String) = emitOnce(text)

    fun onImagePicked(imageRef: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDecodingFromImage = true, errorMessageRes = null) }

            val decoded = qrCodeImageDecoder.decode(imageRef)
            if (decoded != null) {
                _uiState.update { state -> state.copy(isDecodingFromImage = false) }
                emitOnce(decoded)
            } else {
                _uiState.update { state ->
                    state.copy(
                        isDecodingFromImage = false,
                        errorMessageRes = Res.string.image_decode_failed
                    )
                }
            }
        }
    }

    fun onErrorMessageConsumed() {
        _uiState.update { it.copy(errorMessageRes = null) }
    }

    private fun emitOnce(text: String) {
        if (resultEmitted.compareAndSet(false, true)) {
            _scanResult.tryEmit(text)
        }
    }
}

data class QRScannerUiState(
    val isDecodingFromImage: Boolean = false,
    val errorMessageRes: StringResource? = null,
)
