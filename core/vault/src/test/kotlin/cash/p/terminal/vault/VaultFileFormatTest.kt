package cash.p.terminal.vault

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class VaultFileFormatTest {

    private val params = checkNotNull(Argon2Params.ofOrNull(1024, 1, 1))
    private val salt = ByteArray(16) { it.toByte() }
    private val envelopes = List(32) { index -> ByteArray(93) { (it + index).toByte() } }
    private val bytes = VaultFileFormat.encode(VaultFile(params, salt, envelopes))

    @Test
    fun encode_file_writesPrefixThenEnvelopes() {
        val expected = ByteBuffer.allocate(3007)
            .put("PCKV".toByteArray()).put(2)
            .put(1).putInt(1024).putInt(1).put(1).put(salt)
            .also { buffer -> envelopes.forEach(buffer::put) }
            .array()

        assertArrayEquals(expected, bytes)
    }

    @Test
    fun encode_decodedFile_returnsSameBytes() {
        assertArrayEquals(bytes, VaultFileFormat.encode(checkNotNull(VaultFileFormat.decode(bytes))))
    }

    @Test
    fun envelopeAad_index_isFilePrefixPlusIndex() {
        assertArrayEquals(bytes.copyOfRange(0, 31) + 7, VaultFileFormat.envelopeAad(params, salt, 7))
    }

    @Test
    fun decode_truncatedFile_returnsNull() {
        assertNull(VaultFileFormat.decode(bytes.copyOf(bytes.size - 1)))
    }

    @Test
    fun decode_extraByte_returnsNull() {
        assertNull(VaultFileFormat.decode(bytes + 0))
    }

    @Test
    fun decode_wrongMagic_returnsNull() {
        assertNull(VaultFileFormat.decode(bytes.with(0, 'X'.code)))
    }

    @Test
    fun decode_version1Header_returnsNull() {
        assertNull(VaultFileFormat.decode(bytes.with(4, 1)))
    }

    @Test
    fun decode_unknownKdf_returnsNull() {
        assertNull(VaultFileFormat.decode(bytes.with(5, 2)))
    }

    @Test
    fun decode_memoryAbove512MiB_returnsNull() {
        assertNull(VaultFileFormat.decode(bytes.copyOf().also { ByteBuffer.wrap(it).putInt(6, 524_289) }))
    }

    @Test
    fun decode_iterations11_returnsNull() {
        assertNull(VaultFileFormat.decode(bytes.copyOf().also { ByteBuffer.wrap(it).putInt(10, 11) }))
    }

    @Test
    fun decode_parallelism5_returnsNull() {
        assertNull(VaultFileFormat.decode(bytes.with(14, 5)))
    }

    @Test
    fun isLegacy_version1Header_returnsTrue() {
        assertTrue(VaultFileFormat.isLegacy("PCKV".toByteArray() + 1))
    }

    @Test
    fun isLegacy_version2File_returnsFalse() {
        assertFalse(VaultFileFormat.isLegacy(bytes))
    }

    @Test
    fun isLegacy_version2FileWithVersionByteDamagedToOne_returnsFalse() {
        assertFalse(VaultFileFormat.isLegacy(bytes.with(4, 1)))
    }

    @Test
    fun isLegacy_version1HeaderOnFilesOfTheOldSizes_returnsTrue() {
        listOf(5, 100, 693).forEach { size ->
            assertTrue(VaultFileFormat.isLegacy("PCKV".toByteArray() + 1 + ByteArray(size - 5)))
        }
    }

    @Test
    fun isLegacy_shorterThanHeader_returnsFalse() {
        assertFalse(VaultFileFormat.isLegacy("PCKV".toByteArray()))
    }

    @Test
    fun decodePayload_encodedPayload_returnsSameFields() {
        val encoded = VaultFileFormat.encodePayload(EnvelopePayload(23, ByteArray(32) { 1 }, ByteArray(32) { 2 }))

        val decoded = VaultFileFormat.decodePayload(encoded)

        assertEquals(23, decoded.slot)
        assertArrayEquals(ByteArray(32) { 1 }, decoded.slotKey)
        assertArrayEquals(ByteArray(32) { 2 }, decoded.sharedKey)
    }

    @Test
    fun encodePayload_valuesThatWouldBreakTheFixedLayout_throw() {
        val key = ByteArray(32)
        listOf(
            EnvelopePayload(24, key, key),
            EnvelopePayload(-1, key, key),
            EnvelopePayload(0, ByteArray(31), key),
            EnvelopePayload(0, key, ByteArray(33)),
        ).forEach { payload ->
            assertThrows(IllegalArgumentException::class.java) { VaultFileFormat.encodePayload(payload) }
        }
    }

    @Test
    fun ofOrNull_eachBoundExceeded_returnsNull() {
        assertNull(Argon2Params.ofOrNull(1024, 1, 0))
        assertNull(Argon2Params.ofOrNull(1024, 1, 5))
        assertNull(Argon2Params.ofOrNull(1024, 0, 1))
        assertNull(Argon2Params.ofOrNull(1024, 11, 1))
        assertNull(Argon2Params.ofOrNull(31, 1, 4))
        assertNull(Argon2Params.ofOrNull(524_289, 1, 1))
    }

    @Test
    fun ofOrNull_boundaryValues_returnsParams() {
        assertNotNull(Argon2Params.ofOrNull(8, 1, 1))
        assertNotNull(Argon2Params.ofOrNull(32, 10, 4))
        assertNotNull(Argon2Params.ofOrNull(524_288, 1, 1))
    }

    private fun ByteArray.with(index: Int, value: Int): ByteArray = copyOf().also { it[index] = value.toByte() }
}
