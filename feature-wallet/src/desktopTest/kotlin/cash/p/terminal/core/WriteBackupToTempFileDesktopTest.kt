package cash.p.terminal.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission

class WriteBackupToTempFileDesktopTest {

    @Test
    fun writeBackupToTempFile_posixFileSystem_fileIsOwnerOnly() {
        assumeTrue("posix" in FileSystems.getDefault().supportedFileAttributeViews())
        val data = byteArrayOf(1, 2, 3)

        val path = Path.of(writeBackupToTempFile(data))
        try {
            assertEquals(
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                Files.getPosixFilePermissions(path)
            )
            assertArrayEquals(data, Files.readAllBytes(path))
        } finally {
            Files.deleteIfExists(path)
        }
    }
}
