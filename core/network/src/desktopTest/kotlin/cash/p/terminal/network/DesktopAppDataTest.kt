package cash.p.terminal.network

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.assertEquals

class DesktopAppDataTest {

    @Test
    fun restrictToOwner_worldReadableDirectory_leavesOwnerPermissionsOnly() {
        val directory = Files.createTempDirectory("app-data")
        assumeTrue(Files.getFileAttributeView(directory, PosixFileAttributeView::class.java) != null)
        Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwxr-xr-x"))

        restrictToOwner(directory.toFile())

        assertEquals(
            setOf(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE,
            ),
            Files.getPosixFilePermissions(directory),
        )
        directory.toFile().delete()
    }
}
