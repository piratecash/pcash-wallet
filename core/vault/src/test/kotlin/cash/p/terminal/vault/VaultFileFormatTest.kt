package cash.p.terminal.vault

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer

class VaultFileFormatTest {

    private val params = checkNotNull(Argon2Params.ofOrNull(1024, 1, 1))
    private val slot = VaultSlot(params, ByteArray(16) { it.toByte() }, ByteArray(60) { (it + 100).toByte() })
    private val oneSlotFile = VaultFileFormat.encode(VaultFile(listOf(slot)))

    @Test
    fun encode_oneSlot_writesHeaderAndSlotLayout() {
        val expected = ByteBuffer.allocate(91)
            .put("PCKV".toByteArray()).put(1)
            .put(1).putInt(1024).putInt(1).put(1).put(slot.salt).put(slot.sealedMasterKey)
            .array()

        assertArrayEquals(expected, oneSlotFile)
    }

    @Test
    fun encode_decodedFile_returnsSameBytes() {
        val bytes = VaultFileFormat.encode(VaultFile(List(8) { slot }))

        assertArrayEquals(bytes, VaultFileFormat.encode(checkNotNull(VaultFileFormat.decode(bytes))))
    }

    @Test
    fun slotAad_slot_isFileHeaderPlusSlotHeader() {
        assertArrayEquals(oneSlotFile.copyOfRange(0, 31), VaultFileFormat.slotAad(params, slot.salt))
    }

    @Test
    fun decode_truncatedFile_returnsNull() {
        assertNull(VaultFileFormat.decode(oneSlotFile.copyOf(oneSlotFile.size - 1)))
    }

    @Test
    fun decode_extraByte_returnsNull() {
        assertNull(VaultFileFormat.decode(oneSlotFile + 0))
    }

    @Test
    fun decode_wrongMagic_returnsNull() {
        assertNull(VaultFileFormat.decode(oneSlotFile.with(0, 'X'.code)))
    }

    @Test
    fun decode_version2_returnsNull() {
        assertNull(VaultFileFormat.decode(oneSlotFile.with(4, 2)))
    }

    @Test
    fun decode_zeroSlots_returnsNull() {
        assertNull(VaultFileFormat.decode(oneSlotFile.copyOf(5)))
    }

    @Test
    fun decode_nineSlots_returnsNull() {
        assertNull(VaultFileFormat.decode(VaultFileFormat.encode(VaultFile(List(9) { slot }))))
    }

    @Test
    fun decode_unknownKdf_returnsNull() {
        assertNull(VaultFileFormat.decode(oneSlotFile.with(5, 2)))
    }

    @Test
    fun decode_memoryAbove512MiB_returnsNull() {
        val bytes = oneSlotFile.copyOf().also { ByteBuffer.wrap(it).putInt(6, 524_289) }

        assertNull(VaultFileFormat.decode(bytes))
    }

    @Test
    fun decode_iterations11_returnsNull() {
        val bytes = oneSlotFile.copyOf().also { ByteBuffer.wrap(it).putInt(10, 11) }

        assertNull(VaultFileFormat.decode(bytes))
    }

    @Test
    fun decode_parallelism5_returnsNull() {
        assertNull(VaultFileFormat.decode(oneSlotFile.with(14, 5)))
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
