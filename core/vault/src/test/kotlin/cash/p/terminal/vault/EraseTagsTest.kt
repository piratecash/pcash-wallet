package cash.p.terminal.vault

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class EraseTagsTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val file by lazy { File(folder.root, "actions") }
    private val tags by lazy { EraseTags(file) }
    private val kek = ByteArray(32) { 3 }
    private val otherKek = ByteArray(32) { 4 }

    @Test
    fun ensure_missingFile_createsRandomFileOfFixedSize() {
        tags.ensure()
        val first = file.readBytes()
        file.delete()
        tags.ensure()

        assertEquals(EraseTags.FILE_SIZE, first.size)
        assertFalse(first.contentEquals(file.readBytes()))
        assertFalse(tags.matchesAny(kek))
    }

    @Test
    fun ensure_existingFile_keepsIt() {
        tags.ensure()
        tags.set(2, kek)
        val before = file.readBytes()

        tags.ensure()

        assertArrayEquals(before, file.readBytes())
    }

    @Test
    fun set_oneSlot_changesOnlyThatTagAndKeepsTheSize() {
        tags.ensure()
        val before = file.readBytes()

        tags.set(5, kek)

        val after = file.readBytes()
        assertEquals(before.size, after.size)
        assertFalse(before.sliceArray(tagRange(5)).contentEquals(after.sliceArray(tagRange(5))))
        assertTrue(before.indices.filterNot { it in tagRange(5) }.all { before[it] == after[it] })
    }

    @Test
    fun matches_setSlot_trueOnlyForThatSlotAndKek() {
        tags.set(5, kek)

        assertTrue(tags.matches(5, kek))
        assertFalse(tags.matches(6, kek))
        assertFalse(tags.matches(5, otherKek))
        assertTrue(tags.matchesAny(kek))
        assertFalse(tags.matchesAny(otherKek))
    }

    @Test
    fun clear_setSlot_stopsMatchingAndLeavesOtherSlots() {
        tags.set(5, kek)
        tags.set(9, otherKek)

        tags.clear(5)

        assertFalse(tags.matchesAny(kek))
        assertTrue(tags.matches(9, otherKek))
        assertEquals(EraseTags.FILE_SIZE, file.length().toInt())
    }

    @Test
    fun matchesAny_missingOrMalformedFile_returnsFalse() {
        assertFalse(tags.matchesAny(kek))

        tags.set(1, kek)
        val valid = file.readBytes()
        file.writeBytes(valid.copyOf().also { it[0] = 2 })
        assertFalse(tags.matchesAny(kek))
        file.writeBytes(valid + 0)
        assertFalse(tags.matchesAny(kek))
    }

    @Test
    fun ensure_malformedFile_replacesItWithAFixedSizeFile() {
        file.writeBytes(byteArrayOf(1, 2, 3))

        tags.ensure()

        assertEquals(EraseTags.FILE_SIZE, file.length().toInt())
        assertEquals(listOf("actions"), folder.root.list()?.toList())
    }

    private fun EraseTags.set(slot: Int, kek: ByteArray) = publish(slot, EraseTags.tag(kek, slot))

    private fun EraseTags.matches(slot: Int, kek: ByteArray) = equalsAt(slot, EraseTags.tag(kek, slot))

    private fun tagRange(slot: Int): IntRange = 1 + slot * EraseTags.TAG_SIZE until 1 + (slot + 1) * EraseTags.TAG_SIZE
}
