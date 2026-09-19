package cash.p.terminal.modules.send.offline

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import cash.p.terminal.BuildConfig
import cash.p.terminal.core.managers.OfflineTransactionPayloadEncoder
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

class OfflineTransactionFileTransfer(
    private val dispatcherProvider: DispatcherProvider,
    private val payloadEncoder: OfflineTransactionPayloadEncoder,
) {
    suspend fun save(context: Context, uri: Uri, content: String): Boolean =
        withContext(dispatcherProvider.io) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    writeContent(output, content)
                } ?: false
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
        }

    suspend fun createShareUri(context: Context, content: String): Uri? =
        withContext(dispatcherProvider.io) {
            val directory = File(context.cacheDir, SHARE_DIRECTORY)
            try {
                directory.mkdirs()
                cleanupShareDirectory(directory)
                val file = File.createTempFile(SHARE_FILE_PREFIX, FILE_EXTENSION, directory)
                try {
                    file.outputStream().use { output -> writeContent(output, content) }
                    FileProvider.getUriForFile(
                        context,
                        "${BuildConfig.APPLICATION_ID}.fileprovider",
                        file,
                    )
                } catch (e: CancellationException) {
                    file.delete()
                    throw e
                } catch (_: Exception) {
                    file.delete()
                    null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }

    suspend fun read(context: Context, uri: Uri): ReadResult =
        withContext(dispatcherProvider.io) {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    readContent(input)
                } ?: ReadResult.Unavailable
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                ReadResult.Unavailable
            }
        }

    internal suspend fun readContent(input: InputStream): ReadResult {
        val bytes = readBounded(input) ?: return ReadResult.Invalid
        val content = decodeUtf8(bytes) ?: return ReadResult.Invalid
        return if (isSupported(content)) ReadResult.Success(content) else ReadResult.Invalid
    }

    internal suspend fun writeContent(output: OutputStream, content: String): Boolean {
        currentCoroutineContext().ensureActive()
        output.write(content.encodeToByteArray())
        output.flush()
        currentCoroutineContext().ensureActive()
        return true
    }

    internal fun cleanupShareDirectory(
        directory: File,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        directory.listFiles()
            ?.filter { it.isFile && nowMillis - it.lastModified() >= SHARE_FILE_MAX_AGE_MILLIS }
            ?.forEach(File::delete)
    }

    private suspend fun readBounded(input: InputStream): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size() + count > OfflineTransactionPayloadEncoder.MAX_INPUT_CHARACTERS) {
                return null
            }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun decodeUtf8(bytes: ByteArray): String? =
        try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            null
        }

    private fun isSupported(content: String): Boolean =
        when (payloadEncoder.decodeResult(content)) {
            is OfflineTransactionPayloadEncoder.DecodeResult.Decoded -> true
            OfflineTransactionPayloadEncoder.DecodeResult.Invalid -> false
            OfflineTransactionPayloadEncoder.DecodeResult.NotEnvelope ->
                OfflineTransactionPayloadEncoder.isRawTransactionHex(content)
        }

    sealed interface ReadResult {
        data class Success(val content: String) : ReadResult
        data object Invalid : ReadResult
        data object Unavailable : ReadResult
    }

    private companion object {
        const val SHARE_DIRECTORY = "offline-transactions"
        const val SHARE_FILE_PREFIX = "pcash-offline-transaction-"
        const val FILE_EXTENSION = ".txt"
        const val BUFFER_SIZE = 8 * 1024
        const val SHARE_FILE_MAX_AGE_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
}
