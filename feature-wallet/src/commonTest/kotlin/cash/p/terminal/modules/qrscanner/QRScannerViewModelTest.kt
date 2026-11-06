package cash.p.terminal.modules.qrscanner

import cash.p.terminal.qr.multipart.MultipartQrEncoder
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.image_decode_failed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QRScannerViewModelTest {

    private val qrCodeImageDecoder: QrCodeImageDecoder = mockk(relaxed = true)

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = QRScannerViewModel(qrCodeImageDecoder)

    @Test
    fun onFrameScanned_plainQrCode_emitsRawTextOnce() = runTest(dispatcher) {
        val viewModel = createViewModel()
        val raw = "bitcoin:1abcXYZ"

        viewModel.onFrameScanned(raw)

        assertEquals(listOf(raw), viewModel.scanResult.replayCache)
    }

    @Test
    fun onFrameScanned_prefixedButUnparsableText_emitsRawTextOnce() = runTest(dispatcher) {
        val viewModel = createViewModel()
        val raw = "PQR1:HELLO"

        viewModel.onFrameScanned(raw)

        assertEquals(listOf(raw), viewModel.scanResult.replayCache)
    }

    @Test
    fun onFrameScanned_multipartFrames_emitsAssembledText() = runTest(dispatcher) {
        val viewModel = createViewModel()
        val text = "ZZZZZZZZZZ"
        val frames = requireNotNull(MultipartQrEncoder.encode(text, preferredFragmentBytes = 5))

        frames.forEach { viewModel.onFrameScanned(it) }

        assertEquals(listOf(text), viewModel.scanResult.replayCache)
    }

    @Test
    fun onFrameScanned_hexTapeFrames_emitsOriginalHexStringVerbatim() = runTest(dispatcher) {
        val viewModel = createViewModel()
        val hexText = "0123456789abcdef".repeat(80)
        val frames = requireNotNull(MultipartQrEncoder.encode(hexText))

        frames.forEach { viewModel.onFrameScanned(it) }

        assertEquals(listOf(hexText), viewModel.scanResult.replayCache)
    }

    @Test
    fun onFrameScanned_incompleteMultipart_emitsNothing() = runTest(dispatcher) {
        val viewModel = createViewModel()
        val frames = requireNotNull(MultipartQrEncoder.encode("ZZZZZZZZZZ", preferredFragmentBytes = 5))

        viewModel.onFrameScanned(frames.first())

        assertTrue(viewModel.scanResult.replayCache.isEmpty())
    }

    @Test
    fun onFrameScanned_frameFromAnotherMessage_restartsAssembly() = runTest(dispatcher) {
        val viewModel = createViewModel()
        val firstFrames = requireNotNull(MultipartQrEncoder.encode("ZZZZZZZZZZ", preferredFragmentBytes = 5))
        val secondText = "YYYYYYYYYY"
        val secondFrames = requireNotNull(MultipartQrEncoder.encode(secondText, preferredFragmentBytes = 5))

        viewModel.onFrameScanned(firstFrames.first())
        viewModel.onFrameScanned(secondFrames.first())
        viewModel.onFrameScanned(secondFrames.last())

        assertEquals(listOf(secondText), viewModel.scanResult.replayCache)
    }

    @Test
    fun onFrameScanned_secondResultAfterFirst_isIgnored() = runTest(dispatcher) {
        val viewModel = createViewModel()
        val first = "bitcoin:1abcXYZ"
        val second = "bitcoin:1defXYZ"

        viewModel.onFrameScanned(first)
        viewModel.onFrameScanned(second)

        assertEquals(listOf(first), viewModel.scanResult.replayCache)
    }

    @Test
    fun onTextPasted_multipartFrameText_emitsVerbatim() = runTest(dispatcher) {
        val viewModel = createViewModel()
        val frame = requireNotNull(MultipartQrEncoder.encode("ZZZZZZZZZZ", preferredFragmentBytes = 5)).first()

        viewModel.onTextPasted(frame)

        assertEquals(listOf(frame), viewModel.scanResult.replayCache)
    }

    @Test
    fun onImagePicked_afterCameraResult_emitsNothing() = runTest(dispatcher) {
        val viewModel = createViewModel()
        val cameraResult = "bitcoin:1abcXYZ"
        val imageRef = "content://media/image/1"
        coEvery { qrCodeImageDecoder.decode(imageRef) } returns "bitcoin:1decodedFromImage"

        viewModel.onFrameScanned(cameraResult)
        viewModel.onImagePicked(imageRef)

        assertEquals(listOf(cameraResult), viewModel.scanResult.replayCache)
    }

    @Test
    fun onImagePicked_decoderReturnsText_emitsTextAndStopsLoading() = runTest(dispatcher) {
        val viewModel = createViewModel()
        coEvery { qrCodeImageDecoder.decode("image") } returns "bitcoin:1decodedFromImage"

        viewModel.onImagePicked("image")

        assertEquals(listOf("bitcoin:1decodedFromImage"), viewModel.scanResult.replayCache)
        assertFalse(viewModel.uiState.value.isDecodingFromImage)
    }

    @Test
    fun onImagePicked_decoderReturnsNull_showsDecodeErrorUntilConsumed() = runTest(dispatcher) {
        val viewModel = createViewModel()
        coEvery { qrCodeImageDecoder.decode("image") } returns null

        viewModel.onImagePicked("image")

        assertEquals(Res.string.image_decode_failed, viewModel.uiState.value.errorMessageRes)
        assertTrue(viewModel.scanResult.replayCache.isEmpty())

        viewModel.onErrorMessageConsumed()

        assertEquals(null, viewModel.uiState.value.errorMessageRes)
    }

    @Test
    fun scanResult_newCollectorAfterEmission_receivesReplayedValue() = runTest(dispatcher) {
        val viewModel = createViewModel()
        val raw = "bitcoin:1abcXYZ"
        viewModel.onFrameScanned(raw)

        val received = viewModel.scanResult.first()

        assertEquals(raw, received)
    }
}
