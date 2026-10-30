package cash.p.terminal.modules.qrscanner

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.PlanarYUVLuminanceSource
import java.util.concurrent.atomic.AtomicReference

class QrCodeAnalyzer(
    onQrCodeScanned: (String) -> Unit
) : ImageAnalysis.Analyzer {

    // Clearable reference to the scan callback: release() nulls it to break the
    // chain to everything the callback captures. CameraX may keep the analyzer
    // alive on its internal executor thread after teardown; dropping the callback
    // prevents that from leaking the calling screen.
    private val callback = AtomicReference(onQrCodeScanned)

    private val reader = QrCodeReader()

    override fun analyze(image: ImageProxy) {
        try {
            val width = image.width
            val height = image.height
            val rotation = image.imageInfo.rotationDegrees

            val buffer = image.planes[0].buffer
            val rowStride = image.planes[0].rowStride

            buffer.rewind()
            val data = ByteArray(buffer.remaining())
            buffer.get(data)

            val sourceData = if (rowStride == width) data else removePadding(data, width, height, rowStride)

            val (luminance, luminanceWidth, luminanceHeight) = when (rotation % 360) {
                90 -> rotateYUV90(sourceData, width, height, width) to Pair(height, width)
                180 -> rotateYUV180(sourceData, width, height, width) to Pair(width, height)
                270 -> rotateYUV270(sourceData, width, height, width) to Pair(height, width)
                else -> sourceData to Pair(width, height)
            }.let { (bytes, size) -> Triple(bytes, size.first, size.second) }

            val source = PlanarYUVLuminanceSource(
                luminance,
                luminanceWidth,
                luminanceHeight,
                0,
                0,
                luminanceWidth,
                luminanceHeight,
                false
            )

            reader.decode(source, cropToCenter = true)?.let { text ->
                callback.get()?.invoke(text)
            }
        } finally {
            image.close()
        }
    }

    fun release() {
        callback.set(null)
    }

    private fun removePadding(data: ByteArray, width: Int, height: Int, rowStride: Int): ByteArray {
        if (rowStride == width) return data
        val result = ByteArray(width * height)
        for (y in 0 until height) {
            System.arraycopy(data, y * rowStride, result, y * width, width)
        }
        return result
    }

    private fun rotateYUV90(data: ByteArray, width: Int, height: Int, rowStride: Int): ByteArray {
        val rotated = ByteArray(width * height)
        var i = 0
        for (x in 0 until width) {
            for (y in height - 1 downTo 0) {
                rotated[i++] = data[y * rowStride + x]
            }
        }
        return rotated
    }

    private fun rotateYUV180(data: ByteArray, width: Int, height: Int, rowStride: Int): ByteArray {
        val rotated = ByteArray(width * height)
        var i = 0
        for (y in height - 1 downTo 0) {
            for (x in width - 1 downTo 0) {
                rotated[i++] = data[y * rowStride + x]
            }
        }
        return rotated
    }

    private fun rotateYUV270(data: ByteArray, width: Int, height: Int, rowStride: Int): ByteArray {
        val rotated = ByteArray(width * height)
        var i = 0
        for (x in width - 1 downTo 0) {
            for (y in 0 until height) {
                rotated[i++] = data[y * rowStride + x]
            }
        }
        return rotated
    }
}
