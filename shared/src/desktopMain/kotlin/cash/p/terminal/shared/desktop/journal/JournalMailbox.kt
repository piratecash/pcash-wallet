package cash.p.terminal.shared.desktop.journal

import cash.p.terminal.vault.InboxPayload
import cash.p.terminal.vault.JournalInboxCrypto
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.security.SecureRandom

internal enum class MailboxRing(val entrySize: Int) {
    /** Sealed to every slot: each level imports the failed attempts by its own settings. */
    FAILED(JournalInboxCrypto.SLOT_ENTRY_SIZE),

    /** Sealed to one root's notify key: only the parent of a duress level can open them. */
    NOTIFY(JournalInboxCrypto.SINGLE_ENTRY_SIZE),
}

internal class MailboxEntry(val seq: Long, val timestamp: Long, val photo: ByteArray?)

/**
 * `vault/inbox`: `[version][24 public keys][head, nextSeq per ring][failed ring][notify ring]`, always the same size;
 * an unused entry holds a decoy seal. Written in place under [mutex].
 */
internal class JournalMailbox(private val file: File) {
    private val mutex = Mutex()

    /** Changed under [mutex] only: what was made before a wipe must not be written after it. */
    @Volatile
    var wipeGeneration = 0L
        private set

    suspend fun ensure() = mutex.withLock {
        if (!isUsable()) create()
    }

    suspend fun publicKey(slot: Int): ByteArray = locked { it.readAt(keyOffset(slot), JournalInboxCrypto.KEY_SIZE) }

    suspend fun setPublicKey(slot: Int, key: ByteArray) = locked { it.writeAt(keyOffset(slot), key) }

    suspend fun nextSeq(ring: MailboxRing): Long = locked { it.state(ring).nextSeq }

    /** False when a wipe happened since [generation] was read. */
    suspend fun appendFailed(timestamp: Long, photo: ByteArray?, generation: Long): Boolean = locked { access ->
        val keys = List(JournalInboxCrypto.SLOT_COUNT) { access.readAt(keyOffset(it), JournalInboxCrypto.KEY_SIZE) }
        append(access, MailboxRing.FAILED, generation, timestamp, photo) {
            JournalInboxCrypto.sealToSlots(it, keys)
        }
    }

    suspend fun appendNotification(recipient: ByteArray, timestamp: Long, photo: ByteArray?, generation: Long) =
        locked { access ->
            append(access, MailboxRing.NOTIFY, generation, timestamp, photo) {
                JournalInboxCrypto.sealTo(it, recipient)
            }
        }

    /** The entries of [ring] that [open] can read, oldest first. */
    suspend fun read(ring: MailboxRing, open: (ByteArray) -> ByteArray?): List<MailboxEntry> = locked { access ->
        List(RING_SIZE) { access.readAt(entryOffset(ring, it), ring.entrySize) }
            .mapNotNull { entry -> open(entry)?.let(InboxPayload::decode) }
            .map { MailboxEntry(it.seq, it.timestamp, it.photo) }
            .sortedBy(MailboxEntry::seq)
    }

    /** Every entry of both rings becomes a decoy; the header stays, so nothing shows that a wipe happened. */
    suspend fun overwriteEntries() = locked { access ->
        wipeGeneration++
        MailboxRing.entries.forEach { ring ->
            repeat(RING_SIZE) { access.writeAt(entryOffset(ring, it), decoyEntry(ring)) }
        }
    }

    private fun append(
        access: RandomAccessFile,
        ring: MailboxRing,
        generation: Long,
        timestamp: Long,
        photo: ByteArray?,
        seal: (ByteArray) -> ByteArray,
    ): Boolean {
        if (generation != wipeGeneration) return false
        val state = access.state(ring)
        val payload = InboxPayload(state.nextSeq, timestamp, photo).encode()
        access.writeAt(entryOffset(ring, state.head), seal(payload))
        payload.fill(0)
        access.seek(stateOffset(ring))
        access.writeInt((state.head + 1) % RING_SIZE)
        access.writeLong(state.nextSeq + 1)
        return true
    }

    private fun isUsable(): Boolean = file.length() == FILE_SIZE &&
        RandomAccessFile(file, "r").use { it.readByte() == VERSION }

    private fun create() {
        file.parentFile?.mkdirs()
        RandomAccessFile(file, "rw").use { access ->
            access.setLength(0)
            access.writeByte(VERSION.toInt())
            repeat(JournalInboxCrypto.SLOT_COUNT) { access.write(JournalInboxCrypto.decoyPublicKey()) }
            MailboxRing.entries.forEach {
                access.writeInt(0)
                access.writeLong(firstSeq())
            }
            MailboxRing.entries.forEach { ring -> repeat(RING_SIZE) { access.write(decoyEntry(ring)) } }
            access.fd.sync()
        }
    }

    private suspend fun <T> locked(block: (RandomAccessFile) -> T): T = mutex.withLock {
        if (!isUsable()) throw IOException("The inbox file is missing or damaged")
        RandomAccessFile(file, "rw").use { access -> block(access).also { access.fd.sync() } }
    }

    private fun RandomAccessFile.state(ring: MailboxRing): RingState {
        seek(stateOffset(ring))
        return RingState(head = readInt(), nextSeq = readLong())
    }

    private fun RandomAccessFile.readAt(offset: Long, size: Int): ByteArray {
        seek(offset)
        return ByteArray(size).also(::readFully)
    }

    private fun RandomAccessFile.writeAt(offset: Long, bytes: ByteArray) {
        seek(offset)
        write(bytes)
    }

    /** A real seal to keys nobody holds: random bytes would not start with a curve point as a real entry does. */
    private fun decoyEntry(ring: MailboxRing): ByteArray {
        val payload = InboxPayload(0, 0, null).encode()
        return when (ring) {
            MailboxRing.FAILED ->
                JournalInboxCrypto.sealToSlots(payload, List(JournalInboxCrypto.SLOT_COUNT) { decoyKey() })
            MailboxRing.NOTIFY -> JournalInboxCrypto.sealTo(payload, decoyKey())
        }
    }

    private fun decoyKey() = JournalInboxCrypto.decoyPublicKey()

    /** Random, so a recreated mailbox never reuses the numbers its safes have already imported. */
    private fun firstSeq(): Long = random.nextLong(FIRST_SEQ_MIN, FIRST_SEQ_MAX)

    private class RingState(val head: Int, val nextSeq: Long)

    companion object {
        const val RING_SIZE = 50
        private const val FIRST_SEQ_MIN = 1L shl 40
        private const val FIRST_SEQ_MAX = 1L shl 62
        private val random = SecureRandom()
        private const val VERSION: Byte = 1
        private const val KEYS_OFFSET = 1L
        private const val STATE_SIZE = Int.SIZE_BYTES + Long.SIZE_BYTES
        private val STATES_OFFSET = KEYS_OFFSET + JournalInboxCrypto.SLOT_COUNT * JournalInboxCrypto.KEY_SIZE
        private val ENTRIES_OFFSET = STATES_OFFSET + MailboxRing.entries.size * STATE_SIZE
        val FILE_SIZE = ENTRIES_OFFSET + MailboxRing.entries.sumOf { RING_SIZE.toLong() * it.entrySize }

        private fun keyOffset(slot: Int): Long {
            require(slot in 0 until JournalInboxCrypto.SLOT_COUNT) { "Slot $slot is out of range" }
            return KEYS_OFFSET + slot.toLong() * JournalInboxCrypto.KEY_SIZE
        }

        private fun stateOffset(ring: MailboxRing): Long = STATES_OFFSET + ring.ordinal * STATE_SIZE

        private fun entryOffset(ring: MailboxRing, index: Int): Long {
            val before = MailboxRing.entries.take(ring.ordinal).sumOf { RING_SIZE.toLong() * it.entrySize }
            return ENTRIES_OFFSET + before + index.toLong() * ring.entrySize
        }
    }
}
