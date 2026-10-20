package cash.p.terminal.shared.desktop.vault

import cash.p.terminal.core.tryOrNull
import java.io.File
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.StandardOpenOption

/**
 * Keeps two copies of the application off one database.
 * The holder must stay referenced for the whole process: releasing the channel releases the lock.
 */
class SingleInstanceLock private constructor(
    private val channel: FileChannel,
    private val lock: FileLock,
) : AutoCloseable {

    override fun close() {
        tryOrNull { lock.release() }
        tryOrNull { channel.close() }
    }

    companion object {
        /** Null when another instance holds the lock. */
        fun acquire(file: File): SingleInstanceLock? {
            val channel = FileChannel.open(
                file.toPath(),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
            )
            // tryLock returns null for another process and throws for another holder in this JVM.
            val lock = tryOrNull { channel.tryLock() }
            if (lock == null) {
                tryOrNull { channel.close() }
                return null
            }
            return SingleInstanceLock(channel, lock)
        }
    }
}
