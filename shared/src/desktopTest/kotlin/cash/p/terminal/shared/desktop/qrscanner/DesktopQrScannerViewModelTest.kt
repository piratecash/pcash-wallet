package cash.p.terminal.shared.desktop.qrscanner

import cash.p.terminal.core.TestDispatcherProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import io.horizontalsystems.core.DispatcherProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.awt.image.BufferedImage
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopQrScannerViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val runningPreviews = AtomicInteger(0)
    private val cameraCalls = mutableListOf<String>()
    private var blockFirstOpen: CompletableDeferred<Unit>? = null
    private val settings = mockk<DesktopWebcamSettings>(relaxed = true)
    private val session = mockk<DesktopWebcamSession>(relaxed = true) {
        coEvery { open(any(), any()) } coAnswers {
            cameraCalls += "open"
            // A native open blocked in the macOS permission prompt ignores cancellation.
            blockFirstOpen?.let { gate ->
                blockFirstOpen = null
                withContext(NonCancellable) { gate.await() }
            }
            OpenResult.Opened(if (secondArg<String?>() == usb.id) usb else builtIn, listOf(builtIn, usb))
        }
        every { release() } answers { cameraCalls += "release" }
        // Never emits: runCurrent() does not advance time, so the no-frame watchdog stays quiet.
        every { frames() } returns flow<BufferedImage> {
            runningPreviews.incrementAndGet()
            try {
                awaitCancellation()
            } finally {
                runningPreviews.decrementAndGet()
            }
        }
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun stop_openStillBlocked_releasesBeforeTheNextOpenAndNeverAfter() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>().also { blockFirstOpen = it }
        val viewModel = createViewModel()
        viewModel.start()
        runCurrent()

        viewModel.stop()
        viewModel.start()
        runCurrent()
        gate.complete(Unit)
        runCurrent()

        assertEquals(listOf("release", "open", "release", "release", "open"), cameraCalls)
        assertEquals(1, runningPreviews.get())
        viewModel.stop()
        runCurrent()
    }

    @Test
    fun onCameraSelected_sameNameAsAnother_reopensTheSelectedCameraFirst() = runTest(dispatcher) {
        val viewModel = createViewModel()
        viewModel.start()
        runCurrent()

        viewModel.onCameraSelected(usb)
        runCurrent()

        verify { settings.cameraName = usb.name }
        coVerify { session.open(usb.name, usb.id) }
        assertEquals(DesktopCameraState.Preview(listOf(builtIn, usb), usb), viewModel.cameraState.value)
        assertEquals(1, runningPreviews.get())
        viewModel.stop()
        runCurrent()
    }

    @Test
    fun start_afterPauseWithSameNamedCameraSelected_reopensTheSelectedOne() = runTest(dispatcher) {
        every { settings.cameraName } returns usb.name
        val viewModel = createViewModel()
        viewModel.onCameraSelected(usb)
        runCurrent()

        viewModel.stop()
        viewModel.start()
        runCurrent()

        coVerify(exactly = 2) { session.open(usb.name, usb.id) }
        assertEquals(DesktopCameraState.Preview(listOf(builtIn, usb), usb), viewModel.cameraState.value)
        viewModel.stop()
        runCurrent()
    }

    @Test
    fun stop_rightAfterCameraSelected_leavesNoPreviewRunning() = runTest(dispatcher) {
        val viewModel = createViewModel()
        viewModel.start()
        runCurrent()
        assertEquals(1, runningPreviews.get())

        viewModel.onCameraSelected(usb)
        viewModel.stop()
        runCurrent()

        assertEquals(0, runningPreviews.get())
    }

    @Test
    fun stop_whileDecodedTextOnItsWayToMain_deliversNothing() = runTest(dispatcher) {
        // Main runs only when the test says so: the decode finishes on default, then stop() lands first.
        val mainScheduler = TestCoroutineScheduler()
        val dispatchers = mockk<DispatcherProvider> {
            every { default } returns dispatcher
            every { main } returns StandardTestDispatcher(mainScheduler)
        }
        every { session.frames() } returns flow {
            emit(qrImage())
            awaitCancellation()
        }
        val viewModel = createViewModel(dispatchers)
        val delivered = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.scannedText.toList(delivered) }
        viewModel.start()
        runCurrent()

        advanceTimeBy(DECODE_DUE_MS)
        viewModel.stop()
        mainScheduler.runCurrent()
        runCurrent()

        assertEquals(emptyList(), delivered)
    }

    private fun qrImage(): BufferedImage {
        val matrix = QRCodeWriter().encode("bitcoin:1abcXYZ", BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE)
        return BufferedImage(QR_SIZE, QR_SIZE, BufferedImage.TYPE_INT_RGB).apply {
            for (y in 0 until QR_SIZE) {
                for (x in 0 until QR_SIZE) setRGB(x, y, if (matrix[x, y]) BLACK else WHITE)
            }
        }
    }

    private fun createViewModel(
        dispatchers: DispatcherProvider = TestDispatcherProvider(dispatcher, TestScope(dispatcher)),
    ) = DesktopQrScannerViewModel(session, settings, dispatchers)

    private companion object {
        const val DECODE_DUE_MS = 201L
        const val QR_SIZE = 300
        const val BLACK = 0x000000
        const val WHITE = 0xFFFFFF
        val format = WebcamFormat(1280, 720, fourcc("MJPG"), 30)
        val builtIn = WebcamDevice(id = "0", name = "Camera", formats = listOf(format))
        val usb = WebcamDevice(id = "1", name = "Camera", formats = listOf(format))
    }
}
