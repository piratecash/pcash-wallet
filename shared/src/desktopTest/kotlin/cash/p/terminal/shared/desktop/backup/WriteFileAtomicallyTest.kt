package cash.p.terminal.shared.desktop.backup

import cash.p.terminal.shared.desktop.writeFileAtomically
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WriteFileAtomicallyTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun writeFileAtomically_existingFile_replacesContent() {
        val target = existingBackup()

        writeFileAtomically(target, NEW_BYTES)

        assertContentEquals(NEW_BYTES, Files.readAllBytes(target))
        assertEquals(listOf(target), Files.list(target.parent).use { it.toList() })
    }

    @Test
    fun writeFileAtomically_writeFails_keepsPreviousFile() {
        assumeTrue("posix" in FileSystems.getDefault().supportedFileAttributeViews())
        val target = existingBackup()
        val dir = target.parent
        Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("r-x------"))
        try {
            // Root ignores directory permissions, so the failure cannot be provoked.
            assumeFalse(Files.isWritable(dir))

            assertFailsWith<IOException> { writeFileAtomically(target, NEW_BYTES) }

            assertContentEquals(OLD_BYTES, Files.readAllBytes(target))
        } finally {
            Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwx------"))
        }
    }

    @Test
    fun writeFileAtomically_replaceFails_deletesTempFile() {
        val target = folder.newFolder("backup.bin").toPath()
        Files.write(target.resolve("inside"), OLD_BYTES)

        assertFailsWith<IOException> { writeFileAtomically(target, NEW_BYTES) }

        assertEquals(listOf(target), Files.list(target.parent).use { it.toList() })
    }

    private fun existingBackup(): Path =
        folder.newFolder().toPath().resolve("backup.bin").also { Files.write(it, OLD_BYTES) }

    private companion object {
        val OLD_BYTES = byteArrayOf(1, 2, 3)
        val NEW_BYTES = byteArrayOf(4, 5, 6, 7)
    }
}
