package cash.p.terminal.modules.send.offline

import cash.p.terminal.core.managers.OfflineTransactionPayloadEncoder
import io.horizontalsystems.core.DefaultDispatcherProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

class OfflineTransactionFileTransferTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val payloadEncoder = OfflineTransactionPayloadEncoder()
    private val fileTransfer = OfflineTransactionFileTransfer(
        DefaultDispatcherProvider(),
        payloadEncoder,
    )

    @Test
    fun writeContent_rawAndPcash_writesExactUtf8Bytes() = runTest {
        listOf(RAW_CONTENT, PCASH_CONTENT).forEach { content ->
            val output = ByteArrayOutputStream()

            assertTrue(fileTransfer.writeContent(output, content))
            assertArrayEquals(content.encodeToByteArray(), output.toByteArray())
        }
    }

    @Test
    fun readContent_rawHexWithWhitespace_returnsUnchangedText() = runTest {
        val content = " \n$RAW_CONTENT\t"

        val result = fileTransfer.readContent(ByteArrayInputStream(content.encodeToByteArray()))

        assertEquals(OfflineTransactionFileTransfer.ReadResult.Success(content), result)
    }

    @Test
    fun readContent_validPcashPayload_returnsUnchangedText() = runTest {
        val encoder = mockk<OfflineTransactionPayloadEncoder>()
        every { encoder.decodeResult(PCASH_CONTENT) } returns
                OfflineTransactionPayloadEncoder.DecodeResult.Decoded(mockk())
        val transfer = OfflineTransactionFileTransfer(DefaultDispatcherProvider(), encoder)

        val result = transfer.readContent(ByteArrayInputStream(PCASH_CONTENT.encodeToByteArray()))

        assertEquals(OfflineTransactionFileTransfer.ReadResult.Success(PCASH_CONTENT), result)
    }

    @Test
    fun readContent_overLimit_returnsInvalid() = runTest {
        val bytes = ByteArray(OfflineTransactionPayloadEncoder.MAX_INPUT_CHARACTERS + 1) { 'a'.code.toByte() }

        val result = fileTransfer.readContent(ByteArrayInputStream(bytes))

        assertEquals(OfflineTransactionFileTransfer.ReadResult.Invalid, result)
    }

    @Test
    fun readContent_malformedUtf8_returnsInvalid() = runTest {
        val malformed = byteArrayOf(0xC3.toByte(), 0x28)

        val result = fileTransfer.readContent(ByteArrayInputStream(malformed))

        assertEquals(OfflineTransactionFileTransfer.ReadResult.Invalid, result)
    }

    @Test
    fun readContent_unsupportedText_returnsInvalid() = runTest {
        val result = fileTransfer.readContent(ByteArrayInputStream("not a transaction".encodeToByteArray()))

        assertEquals(OfflineTransactionFileTransfer.ReadResult.Invalid, result)
    }

    @Test
    fun cleanupShareDirectory_expiredAndRecentFiles_deletesOnlyExpired() {
        val directory = temporaryFolder.newFolder("shares")
        val expired = directory.resolve("expired.txt").apply {
            writeText(RAW_CONTENT)
            setLastModified(NOW - EIGHT_DAYS_MILLIS)
        }
        val recent = directory.resolve("recent.txt").apply {
            writeText(RAW_CONTENT)
            setLastModified(NOW)
        }

        fileTransfer.cleanupShareDirectory(directory, NOW)

        assertFalse(expired.exists())
        assertTrue(recent.exists())
    }

    @Test
    fun readContent_cancelledDuringRead_propagatesCancellation() = runTest {
        lateinit var read: Deferred<OfflineTransactionFileTransfer.ReadResult>
        read = async(start = CoroutineStart.LAZY) {
            fileTransfer.readContent(CancellingInputStream { read.cancel() })
        }

        read.start()

        try {
            read.await()
            throw AssertionError("Expected cancellation")
        } catch (_: CancellationException) {
            assertTrue(read.isCancelled)
        }
    }

    private class CancellingInputStream(
        private val cancel: () -> Unit,
    ) : InputStream() {
        private var delivered = false

        override fun read(): Int = error("Buffered read expected")

        override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
            if (delivered) return -1
            delivered = true
            bytes[offset] = 'a'.code.toByte()
            cancel()
            return 1
        }
    }

    private companion object {
        val RAW_CONTENT = "Aa".repeat(32)
        const val PCASH_CONTENT = "pcash:tx:v1:beam:payload"
        const val NOW = 10_000_000_000L
        const val EIGHT_DAYS_MILLIS = 8L * 24 * 60 * 60 * 1000
    }
}
