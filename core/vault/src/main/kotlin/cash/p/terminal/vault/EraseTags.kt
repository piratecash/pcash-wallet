package cash.p.terminal.vault

import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * `[version][24 tags]`, always the same size; a slot without an erase password holds random bytes.
 * A file of any other shape holds no tags.
 */
internal class EraseTags(file: File) {
    private val storage = AtomicFile(file)
    private val secureRandom = SecureRandom()

    fun ensure() {
        if (read() == null) storage.replace(encode(randomTags()))
    }

    fun publish(slot: Int, tag: ByteArray) {
        require(tag.size == TAG_SIZE) { "An erase tag is $TAG_SIZE bytes" }
        update(slot, tag)
    }

    fun clear(slot: Int) = update(slot, randomTag())

    fun equalsAt(slot: Int, expected: ByteArray): Boolean =
        read()?.let { MessageDigest.isEqual(it[slot], expected) } == true

    /** Compares every slot without an early exit, so the time does not reveal which one matched. */
    fun matchesAny(kek: ByteArray): Boolean {
        val tags = read() ?: return false
        var matched = false
        tags.forEachIndexed { slot, stored -> matched = MessageDigest.isEqual(stored, tag(kek, slot)) or matched }
        return matched
    }

    private fun update(slot: Int, tag: ByteArray) {
        val tags = (read() ?: randomTags()).toMutableList()
        tags[slot] = tag
        storage.replace(encode(tags))
    }

    private fun read(): List<ByteArray>? {
        val bytes = storage.read() ?: return null
        if (bytes.size != FILE_SIZE || bytes[0] != VERSION) return null
        return List(PasswordVault.SLOT_COUNT) { bytes.copyOfRange(1 + it * TAG_SIZE, 1 + (it + 1) * TAG_SIZE) }
    }

    private fun encode(tags: List<ByteArray>): ByteArray {
        val buffer = ByteBuffer.allocate(FILE_SIZE).put(VERSION)
        tags.forEach(buffer::put)
        return buffer.array()
    }

    private fun randomTags(): List<ByteArray> = List(PasswordVault.SLOT_COUNT) { randomTag() }

    private fun randomTag(): ByteArray = ByteArray(TAG_SIZE).also(secureRandom::nextBytes)

    companion object {
        const val TAG_SIZE = 32
        const val FILE_SIZE = 1 + PasswordVault.SLOT_COUNT * TAG_SIZE

        private const val VERSION: Byte = 1
        private val LABEL = "pcash-erase-contacts-v1".toByteArray(Charsets.US_ASCII)

        fun tag(kek: ByteArray, slot: Int): ByteArray = hmacSha256(kek, LABEL, byteArrayOf(slot.toByte()))
    }
}
