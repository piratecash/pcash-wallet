package cash.p.terminal.shared.desktop.journal

import cash.p.terminal.core.storage.desktop.VaultSafeDao
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.shared.desktop.qrscanner.DesktopWebcamSession
import cash.p.terminal.shared.desktop.qrscanner.DesktopWebcamSettings
import cash.p.terminal.shared.desktop.qrscanner.OpenResult
import cash.p.terminal.vault.InboxPayload
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.stream.MemoryCacheImageOutputStream
import kotlin.math.min
import kotlin.time.Duration.Companion.seconds

interface SelfieCamera {
    /** One JPEG of at most [InboxPayload.MAX_PHOTO_SIZE] bytes, kept in memory only; null when there is none. */
    suspend fun capture(): ByteArray?
}

/** A failed capture is remembered in [safe] for the settings badge. */
internal suspend fun SelfieCamera.captureFor(safe: VaultSafeDao?): ByteArray? = capture().also { photo ->
    tryOrNull { safe?.putFlag(JournalSetting.SELFIE_PROBLEM, photo == null) }
}

/** One webcam session per photo, released right after; callers arriving meanwhile share the photo being taken. */
class DesktopSelfieCamera(
    private val webcamSettings: DesktopWebcamSettings,
    private val dispatcherProvider: DispatcherProvider,
) : SelfieCamera {
    private val mutex = Mutex()

    // Guarded by the mutex.
    private var inFlight: Deferred<ByteArray?>? = null

    override suspend fun capture(): ByteArray? = mutex.withLock {
        inFlight?.takeIf { it.isActive } ?: dispatcherProvider.applicationScope.async(dispatcherProvider.io) {
            withTimeoutOrNull(CAPTURE_TIMEOUT) { takePhoto() }
        }.also { inFlight = it }
    }.await()

    private suspend fun takePhoto(): ByteArray? {
        val session = DesktopWebcamSession()
        try {
            if (session.open(webcamSettings.cameraName) !is OpenResult.Opened) return null
            // The first frames of a just opened camera are dark while its exposure settles.
            delay(WARM_UP)
            return encodeSelfie(session.frames().first())
        } finally {
            session.close()
        }
    }

    private companion object {
        val CAPTURE_TIMEOUT = 8.seconds
        val WARM_UP = 1.seconds
    }
}

private const val MAX_WIDTH = 1280
private const val MAX_HEIGHT = 720
private val JPEG_QUALITIES = listOf(0.85f, 0.7f, 0.55f, 0.4f, 0.25f)

/** Scaled down to fit 1280×720, at the best quality that stays within the mailbox's photo size. */
internal fun encodeSelfie(frame: BufferedImage): ByteArray? {
    val image = fitted(frame)
    return JPEG_QUALITIES.asSequence().map { jpeg(image, it) }.firstOrNull { it.size <= InboxPayload.MAX_PHOTO_SIZE }
}

private fun fitted(frame: BufferedImage): BufferedImage {
    val scale = min(1.0, min(MAX_WIDTH.toDouble() / frame.width, MAX_HEIGHT.toDouble() / frame.height))
    val width = (frame.width * scale).toInt().coerceAtLeast(1)
    val height = (frame.height * scale).toInt().coerceAtLeast(1)
    return BufferedImage(width, height, BufferedImage.TYPE_INT_RGB).also { target ->
        val graphics = target.createGraphics()
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        graphics.drawImage(frame, 0, 0, width, height, null)
        graphics.dispose()
    }
}

private fun jpeg(image: BufferedImage, quality: Float): ByteArray {
    val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
    val output = ByteArrayOutputStream()
    try {
        // ImageIO would otherwise buffer the image in a plaintext temp file.
        MemoryCacheImageOutputStream(output).use { stream ->
            writer.output = stream
            val parameters = writer.defaultWriteParam.apply {
                compressionMode = ImageWriteParam.MODE_EXPLICIT
                compressionQuality = quality
            }
            writer.write(null, IIOImage(image, null, null), parameters)
        }
    } finally {
        writer.dispose()
    }
    return output.toByteArray()
}
