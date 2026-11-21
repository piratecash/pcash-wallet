package cash.p.terminal.core

import android.content.ContentResolver
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream

class SaveBackupTest {

    private val uri = mockk<Uri>()
    private val resolver = mockk<ContentResolver>()
    private val bytes = byteArrayOf(1, 2, 3)

    private var saves = 0
    private var error: Throwable? = null

    private fun save() = resolver.saveBackup(uri, bytes, onSave = { saves++ }, onError = { error = it })

    // Any mode: these tests are about failure handling; the mode has its own test.
    private fun stubOpen(stream: OutputStream?) {
        every { resolver.openOutputStream(uri) } returns stream
        every { resolver.openOutputStream(uri, any()) } returns stream
    }

    @Test
    fun saveBackup_closeFails_reportsErrorNotSave() {
        val closeError = IOException("close failed")
        stubOpen(object : OutputStream() {
            override fun write(b: Int) = Unit
            override fun close() = throw closeError
        })

        save()

        assertEquals(0, saves)
        assertSame(closeError, error)
    }

    @Test
    fun saveBackup_noStream_reportsError() {
        stubOpen(null)

        save()

        assertEquals(0, saves)
        assertTrue(error is IOException)
    }

    @Test
    fun saveBackup_openFails_reportsError() {
        val openError = FileNotFoundException("gone")
        every { resolver.openOutputStream(uri) } throws openError
        every { resolver.openOutputStream(uri, any()) } throws openError

        save()

        assertEquals(0, saves)
        assertSame(openError, error)
    }

    @Test
    fun saveBackup_opensInTruncateMode_writesAllBytes() {
        val stream = ByteArrayOutputStream()
        every { resolver.openOutputStream(uri, "wt") } returns stream

        save()

        assertArrayEquals(bytes, stream.toByteArray())
        assertEquals(1, saves)
        assertEquals(null, error)
    }

    @Test
    fun saveBackup_success_reportsSaveAfterClose() {
        var closed = false
        stubOpen(object : OutputStream() {
            override fun write(b: Int) = Unit
            override fun close() {
                closed = true
            }
        })
        var closedAtSave: Boolean? = null

        resolver.saveBackup(uri, bytes, onSave = { closedAtSave = closed }, onError = { error = it })

        assertEquals(true, closedAtSave)
        assertEquals(null, error)
    }
}
