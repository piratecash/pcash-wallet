/*
 * Camera and format selection ported from Sparrow Wallet's WebcamService
 * (https://github.com/sparrowwallet/sparrow), licensed under the Apache License, Version 2.0.
 * Modified for P.CASH: rewritten in Kotlin over plain data classes.
 */
package cash.p.terminal.shared.desktop.qrscanner

import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds

data class WebcamFormat(val width: Int, val height: Int, val fourcc: Int, val fps: Int)

/** [id] tells apart identical cameras; the user's choice is remembered by [name], which survives reconnects. */
data class WebcamDevice(val id: String, val name: String, val formats: List<WebcamFormat>)

enum class DesktopOs {
    MacOs, Windows, Linux;

    companion object {
        val current: DesktopOs = System.getProperty("os.name").orEmpty().let { name ->
            when {
                name.startsWith("Mac", ignoreCase = true) -> MacOs
                name.startsWith("Windows", ignoreCase = true) -> Windows
                else -> Linux
            }
        }
    }
}

/** A started stream may still never deliver a frame (Linux and Windows report that failure asynchronously). */
internal val NO_FRAMES_TIMEOUT = 5.seconds

private const val MAX_WIDTH = 1920
private const val MAX_HEIGHT = 1080
private const val TARGET_PIXELS = 1280 * 720

// V4L2 formats openpnp-capture converts on Linux, best first; macOS and Windows abstract the pixel format away.
private val linuxPixelFormats = listOf("RGB3", "YUYV", "MJPG").map(::fourcc)

/** Cameras without formats cannot be opened; the just selected one, then the remembered name, is tried first. */
internal fun orderDevices(
    devices: List<WebcamDevice>,
    rememberedName: String?,
    selectedId: String? = null,
): List<WebcamDevice> {
    val priority = compareByDescending<WebcamDevice> { it.id == selectedId }
        .thenByDescending { it.name == rememberedName }
    return devices.filter { it.formats.isNotEmpty() }.sortedWith(priority)
}

internal fun chooseFormat(formats: List<WebcamFormat>, os: DesktopOs): WebcamFormat? {
    val candidates = formats.filter { it.width <= MAX_WIDTH && it.height <= MAX_HEIGHT }.ifEmpty { formats }
    val byResolution = compareBy<WebcamFormat> { abs(it.width * it.height - TARGET_PIXELS) }.thenByDescending { it.fps }
    val order = if (os == DesktopOs.Linux) compareBy(::linuxPixelPriority).then(byResolution) else byResolution
    return candidates.minWithOrNull(order)
}

/** The camera to try after [current] delivered no frame within [NO_FRAMES_TIMEOUT]; `null` when none is left. */
internal fun nextAfterNoFrames(order: List<WebcamDevice>, current: WebcamDevice): WebcamDevice? {
    val index = order.indexOf(current)
    return if (index < 0) null else order.getOrNull(index + 1)
}

private fun linuxPixelPriority(format: WebcamFormat): Int =
    linuxPixelFormats.indexOf(format.fourcc).takeIf { it >= 0 } ?: linuxPixelFormats.size

internal fun fourcc(code: String): Int =
    code.foldIndexed(0) { index, value, char -> value or (char.code shl (index * Byte.SIZE_BITS)) }
