package cash.p.terminal.shared.desktop.vault

import cash.p.terminal.core.tryOrNull
import cash.p.terminal.vault.PasswordVault
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom
import java.util.Random
import kotlin.math.exp
import kotlin.math.ln

/** Keeps the vault directory the same shape whatever it holds: every slot always has its files. */
internal class VaultSlots(
    private val files: DesktopVaultFiles,
    private val random: Random = SecureRandom(),
) {

    fun prepareAll(occupiedSlot: Int) {
        files.vaultDirectory.mkdirs()
        for (slot in 0 until PasswordVault.SLOT_COUNT) {
            slotFiles(slot).forEach { lockFile(it).createNewFile() }
            if (slot != occupiedSlot) erase(slot)
        }
        lockFile(files.sharedDatabaseFile).createNewFile()
    }

    /** Overwrites what the slot held and leaves bytes no key opens, of a size a real database could have. */
    fun erase(slot: Int) {
        slotFiles(slot).forEach { file ->
            transientFiles(file).forEach(File::delete)
            overwriteWithNoise(file)
        }
    }

    /** Log-uniform: real safes run from nearly empty to a full log, and a size past the range would mark one real. */
    fun noisePages(): Int = pagesBetween(MIN_PAGES, MAX_PAGES)

    fun pagesBetween(min: Int, max: Int): Int {
        val pages = min * exp(random.nextDouble() * ln(max.toDouble() / min))
        return pages.toInt().coerceIn(min, max)
    }

    fun clearForNewSafe(slot: Int) {
        slotFiles(slot).forEach { file -> (transientFiles(file) + file).forEach(File::delete) }
    }

    /** Real files and decoys must not differ in mode or any date, nor show which level was used last. */
    fun alignFileAttributes() {
        // Listing a directory touches its access time, so the walk finishes before anything is set.
        val entries = listOf(files.vaultFile) +
            listOf(files.vaultDirectory, files.kitsDirectory).flatMap { it.walkTopDown().toList() }
        val now = FileTime.fromMillis(System.currentTimeMillis())
        entries.forEach { tryOrNull { align(it.toPath(), now) } }
    }

    private fun align(path: Path, time: FileTime) {
        Files.getFileAttributeView(path, PosixFileAttributeView::class.java, LinkOption.NOFOLLOW_LINKS)
            ?.setPermissions(if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) OWNER_DIRECTORY else OWNER_FILE)
        Files.getFileAttributeView(path, BasicFileAttributeView::class.java).setTimes(time, time, time)
    }

    private fun slotFiles(slot: Int) = listOf(files.safeFile(slot), files.premiumFile(slot))

    private fun lockFile(database: File) = File("${database.path}$LOCK_SUFFIX")

    private fun transientFiles(database: File) = TRANSIENT_SUFFIXES.map { File("${database.path}$it") }

    private fun overwriteWithNoise(file: File) = writeNoise(file, noisePages())

    fun writeNoise(file: File, pages: Int) {
        val size = pages.toLong() * PAGE_SIZE
        RandomAccessFile(file, "rw").use { target ->
            val page = ByteArray(PAGE_SIZE)
            repeat(((maxOf(target.length(), size) + PAGE_SIZE - 1) / PAGE_SIZE).toInt()) {
                random.nextBytes(page)
                target.write(page)
            }
            target.setLength(size)
            target.fd.sync()
        }
    }

    private companion object {
        const val LOCK_SUFFIX = ".lck"
        val TRANSIENT_SUFFIXES = listOf("-wal", "-shm", "-journal")
        const val PAGE_SIZE = 4096
        const val MIN_PAGES = 8
        const val MAX_PAGES = 4096
        val OWNER_FILE: Set<PosixFilePermission> = PosixFilePermissions.fromString("rw-------")
        val OWNER_DIRECTORY: Set<PosixFilePermission> = PosixFilePermissions.fromString("rwx------")
    }
}
