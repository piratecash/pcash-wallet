package cash.p.terminal.shared.desktop.vault

import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class VaultSlotsAttributesTest {

    private lateinit var directory: File
    private lateinit var files: DesktopVaultFiles

    @BeforeTest
    fun setUp() {
        directory = createTempDirectory("vault-attributes").toFile()
        files = DesktopVaultFiles(directory)
        files.vaultFile.writeText("vault")
        files.vaultDirectory.mkdirs()
        entry(files.safeFile(0), "rw-r--r--", ageDays = 3)
        entry(files.safeFile(1), "rw-------", ageDays = 1)
        val decoy = File(files.kitsDirectory, "decoy").apply { mkdirs() }
        val real = File(files.kitsDirectory, "real").apply { mkdirs() }
        entry(File(decoy, "noise"), "rw-r--r--", ageDays = 3)
        entry(File(real, "db"), "rw-------", ageDays = 1)
        mode(decoy, "rwxr-xr-x")
        mode(real, "rwx------")
    }

    @AfterTest
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun alignFileAttributes_realAndDecoyEntries_shareOwnerOnlyModePerKind() {
        assumeTrue(posixSupported())
        val entries = entries()

        VaultSlots(files).alignFileAttributes()

        entries.forEach { file ->
            val expected = if (file.isDirectory) "rwx------" else "rw-------"
            assertEquals(expected, PosixFilePermissions.toString(Files.getPosixFilePermissions(file.toPath())), "$file")
        }
    }

    @Test
    fun alignFileAttributes_realAndDecoyEntries_shareModificationAndAccessTime() {
        val entries = entries()

        VaultSlots(files).alignFileAttributes()

        val attributes = entries.map(::attributes)
        assertEquals(1, attributes.map { it.lastModifiedTime() }.toSet().size)
        assertEquals(1, attributes.map { it.lastAccessTime() }.toSet().size)
    }

    @Test
    fun alignFileAttributes_realAndDecoyEntries_shareCreationTime() {
        assumeTrue(creationTimeSettable())
        val entries = entries()

        VaultSlots(files).alignFileAttributes()

        assertEquals(1, entries.map { attributes(it).creationTime() }.toSet().size)
    }

    @Test
    fun alignFileAttributes_entryInVaultFails_kitsEntriesStillAligned() {
        // Windows refuses symbolic links without Developer Mode or the privilege.
        val linked = runCatching {
            Files.createSymbolicLink(File(files.vaultDirectory, "dangling").toPath(), File(directory, "missing").toPath())
        }.isSuccess
        assumeTrue(linked)
        val kits = files.kitsDirectory.walkTopDown().toList()

        VaultSlots(files).alignFileAttributes()

        assertEquals(1, (kits + files.vaultFile).map { attributes(it).lastModifiedTime() }.toSet().size)
    }

    private fun entries(): List<File> = listOf(files.vaultFile) +
        listOf(files.vaultDirectory, files.kitsDirectory).flatMap { it.walkTopDown().toList() }

    private fun entry(file: File, permissions: String, ageDays: Long) {
        file.writeText(file.name)
        mode(file, permissions)
        val time = FileTime.fromMillis(System.currentTimeMillis() - ageDays * DAY_MS)
        view(file.toPath()).setTimes(time, time, time)
    }

    private fun mode(file: File, permissions: String) {
        if (posixSupported()) Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString(permissions))
    }

    private fun attributes(file: File): BasicFileAttributes =
        Files.readAttributes(file.toPath(), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)

    private fun view(path: Path) = Files.getFileAttributeView(path, BasicFileAttributeView::class.java)

    private fun posixSupported() =
        Files.getFileAttributeView(directory.toPath(), PosixFileAttributeView::class.java) != null

    /** Linux keeps a birth time it does not let a process set; there the JDK silently skips it. */
    private fun creationTimeSettable(): Boolean {
        val probe = File(directory, "probe").apply { writeText("probe") }
        val old = FileTime.fromMillis(System.currentTimeMillis() - 10 * DAY_MS)
        view(probe.toPath()).setTimes(old, old, old)
        // macOS reads the birth time back in whole seconds.
        val settable = attributes(probe).creationTime().to(TimeUnit.SECONDS) == old.to(TimeUnit.SECONDS)
        probe.delete()
        return settable
    }

    private companion object {
        const val DAY_MS = 86_400_000L
    }
}
