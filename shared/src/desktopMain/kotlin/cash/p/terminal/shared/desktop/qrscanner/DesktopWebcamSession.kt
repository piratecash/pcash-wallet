/*
 * Device fallback ported from Sparrow Wallet's WebcamService
 * (https://github.com/sparrowwallet/sparrow), licensed under the Apache License, Version 2.0.
 * Modified for P.CASH: coroutines, a pinned camera thread, a no-frame watchdog.
 */
package cash.p.terminal.shared.desktop.qrscanner

import cash.p.terminal.core.tryOrNull
import co.touchlab.kermit.Logger
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.openpnp.capture.CaptureDevice
import org.openpnp.capture.CaptureStream
import org.openpnp.capture.OpenPnpCapture
import org.openpnp.capture.OpenPnpCapture.fourCcToString

sealed interface OpenResult {
    data class Opened(val device: WebcamDevice, val devices: List<WebcamDevice>) : OpenResult

    /** No camera, or access denied: macOS reports a denial as an empty device list. */
    data object NoDevices : OpenResult

    /** Every camera is busy or unsupported, or the native library is missing. */
    data object AllFailed : OpenResult
}

/**
 * One scanner page's camera. Every native call runs on one dedicated thread: Windows binds COM to the
 * thread that created the context, and the macOS context constructor blocks on the permission prompt.
 */
class DesktopWebcamSession {

    private val executor = Executors.newSingleThreadExecutor { Thread(it, "qr-webcam").apply { isDaemon = true } }
    private val cameraDispatcher = executor.asCoroutineDispatcher()
    private val closed = AtomicBoolean(false)

    // Touched only on the camera thread.
    private var capture: OpenPnpCapture? = null
    private var stream: CaptureStream? = null
    private var nativeDevices: Map<WebcamDevice, CaptureDevice> = emptyMap()
    private var order: List<WebcamDevice> = emptyList()
    private var current: WebcamDevice? = null

    /** Enumerates the cameras anew and opens the first working one, [preferredId] then [preferredName] first. */
    suspend fun open(preferredName: String?, preferredId: String? = null): OpenResult = withContext(cameraDispatcher) {
        releaseNow()
        val context = if (closed.get() || !isLibraryPackaged()) null else tryOrNull { OpenPnpCapture() }
        capture = context
        val devices = context?.let { tryOrNull { it.getDevices().associateBy { device -> device.toWebcamDevice() } } }
        if (devices == null) return@withContext OpenResult.AllFailed
        logger.i { "Cameras found: ${devices.size}" }
        nativeDevices = devices
        order = orderDevices(devices.keys.toList(), preferredName, preferredId)
        if (devices.isEmpty()) OpenResult.NoDevices else openFirstWorking(order)
    }

    /** After [NO_FRAMES_TIMEOUT] without a frame: the cameras after the current one, in order. */
    suspend fun openNext(): OpenResult = withContext(cameraDispatcher) {
        val next = current?.let { nextAfterNoFrames(order, it) }
        closeStream()
        if (next == null) OpenResult.AllFailed else openFirstWorking(order.drop(order.indexOf(next)))
    }

    fun frames(): Flow<BufferedImage> = flow {
        while (true) {
            captureNewFrame()?.let { emit(it) }
            delay(FRAME_POLL_INTERVAL_MS)
        }
    }.flowOn(cameraDispatcher)

    /** Closes the stream and the context; the session can be opened again. */
    fun release() = onCameraThread(::releaseNow)

    fun close() {
        closed.set(true)
        onCameraThread(::releaseNow)
        executor.shutdown()
    }

    private fun openFirstWorking(candidates: List<WebcamDevice>): OpenResult {
        if (closed.get()) return OpenResult.AllFailed
        val opened = candidates.firstOrNull(::tryOpen) ?: return OpenResult.AllFailed
        return OpenResult.Opened(opened, order)
    }

    private fun tryOpen(device: WebcamDevice): Boolean {
        val format = chooseFormat(device.formats, DesktopOs.current) ?: return false
        val native = nativeDevices[device] ?: return false
        val opened = tryOrNull { native.openStream(native.formats[device.formats.indexOf(format)]) }
        if (opened == null) {
            logger.w { "Camera failed to open, trying the next one" }
            return false
        }
        logger.i { "Camera opened: ${format.width}x${format.height} ${fourCcToString(format.fourcc)}" }
        stream = opened
        current = device
        return true
    }

    private fun captureNewFrame(): BufferedImage? {
        val active = stream?.takeIf { !closed.get() } ?: return null
        return tryOrNull { if (active.hasNewFrame()) active.capture() else null }
    }

    private fun closeStream() {
        stream?.let { tryOrNull { it.close() } }
        stream = null
        current = null
    }

    private fun releaseNow() {
        closeStream()
        capture?.let { tryOrNull { it.close() } }
        capture = null
        nativeDevices = emptyMap()
        order = emptyList()
    }

    // A release queued after close() is already covered by the release close() queued.
    private fun onCameraThread(block: () -> Unit) {
        try {
            executor.execute(block)
        } catch (_: RejectedExecutionException) {
        }
    }

    // Without the packaged copy JNA would search the system paths for some other openpnp-capture build.
    private fun isLibraryPackaged(): Boolean {
        val libraryDir = System.getProperty(JNA_LIBRARY_PATH) ?: return false
        return File(libraryDir, System.mapLibraryName(LIBRARY_NAME)).isFile
    }

    private fun CaptureDevice.toWebcamDevice() = WebcamDevice(
        id = uniqueId,
        name = name,
        formats = formats.map { it.formatInfo.run { WebcamFormat(width, height, fourcc, fps) } },
    )

    private companion object {
        val logger = Logger.withTag("DesktopWebcam")
        const val FRAME_POLL_INTERVAL_MS = 33L
        const val JNA_LIBRARY_PATH = "jna.library.path"
        const val LIBRARY_NAME = "openpnp-capture"
    }
}
