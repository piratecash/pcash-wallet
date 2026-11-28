package cash.p.terminal.vault

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.random.Random

class JournalInboxCryptoTest {

    private val keys = List(JournalInboxCrypto.SLOT_COUNT) { JournalInboxCrypto.generateKeyPair() }
    private val publicKeys = keys.map { it.publicKey }
    private val payload = InboxPayload(seq = 7, timestamp = 1_700_000_000_000, photo = byteArrayOf(1, 2, 3)).encode()

    @Test
    fun openSlot_everyIndex_returnsThePayloadOnlyForItsOwnIndex() {
        val entry = JournalInboxCrypto.sealToSlots(payload, publicKeys)

        keys.forEachIndexed { index, pair ->
            assertArrayEquals(payload, JournalInboxCrypto.openSlot(entry, index, pair.privateKey))
            val otherIndex = (index + 1) % JournalInboxCrypto.SLOT_COUNT
            assertNull(JournalInboxCrypto.openSlot(entry, otherIndex, pair.privateKey))
        }
    }

    @Test
    fun openSlot_foreignKey_returnsNull() {
        val entry = JournalInboxCrypto.sealToSlots(payload, publicKeys)
        val foreign = JournalInboxCrypto.generateKeyPair().privateKey

        repeat(JournalInboxCrypto.SLOT_COUNT) { assertNull(JournalInboxCrypto.openSlot(entry, it, foreign)) }
    }

    @Test
    fun sealToSlots_decoyKeys_areUsableRecipientsThatNobodyOpens() {
        val decoys = List(JournalInboxCrypto.SLOT_COUNT) { JournalInboxCrypto.decoyPublicKey() }
        val withOneReal = decoys.toMutableList().apply { set(4, keys[4].publicKey) }

        val entry = JournalInboxCrypto.sealToSlots(payload, withOneReal)

        assertArrayEquals(payload, JournalInboxCrypto.openSlot(entry, 4, keys[4].privateKey))
        keys.forEachIndexed { index, pair ->
            if (index != 4) assertNull(JournalInboxCrypto.openSlot(entry, index, pair.privateKey))
        }
    }

    @Test
    fun openSlot_randomBytesOrWrongShape_returnsNull() {
        val random = Random.nextBytes(JournalInboxCrypto.SLOT_ENTRY_SIZE)
        val entry = JournalInboxCrypto.sealToSlots(payload, publicKeys)
        val privateKey = keys[0].privateKey

        assertNull(JournalInboxCrypto.openSlot(random, 0, privateKey))
        assertNull(JournalInboxCrypto.openSlot(entry.copyOf(entry.size - 1), 0, privateKey))
        assertNull(JournalInboxCrypto.openSlot(entry, -1, privateKey))
        assertNull(JournalInboxCrypto.openSlot(entry, JournalInboxCrypto.SLOT_COUNT, privateKey))
        assertNull(JournalInboxCrypto.openSlot(entry, 0, privateKey.copyOf(31)))
        assertNull(JournalInboxCrypto.openSlot(ByteArray(0), 0, privateKey))
    }

    @Test
    fun openSlot_lowOrderEphemeralKey_returnsNull() {
        val entry = JournalInboxCrypto.sealToSlots(payload, publicKeys)
        ByteArray(JournalInboxCrypto.KEY_SIZE).copyInto(entry)

        assertNull(JournalInboxCrypto.openSlot(entry, 0, keys[0].privateKey))
    }

    @Test
    fun openSlot_anyTamperedRegion_returnsNull() {
        val entry = JournalInboxCrypto.sealToSlots(payload, publicKeys)
        val ownWrap = JournalInboxCrypto.KEY_SIZE + 2 * WRAP_SIZE + 20
        val body = JournalInboxCrypto.KEY_SIZE + JournalInboxCrypto.SLOT_COUNT * WRAP_SIZE + 100

        for (offset in listOf(5, ownWrap, body, entry.size - 1)) {
            val tampered = entry.copyOf().also { it[offset] = (it[offset] + 1).toByte() }
            assertNull("offset $offset", JournalInboxCrypto.openSlot(tampered, 2, keys[2].privateKey))
        }
    }

    @Test
    fun sealToSlots_emptyAndMaxPhoto_haveTheSameConstantLength() {
        val empty = InboxPayload(1, 2, null).encode()
        val max = InboxPayload(1, 2, ByteArray(InboxPayload.MAX_PHOTO_SIZE) { 9 }).encode()

        val emptyEntry = JournalInboxCrypto.sealToSlots(empty, publicKeys)
        val maxEntry = JournalInboxCrypto.sealToSlots(max, publicKeys)

        assertEquals(JournalInboxCrypto.SLOT_ENTRY_SIZE, emptyEntry.size)
        assertEquals(JournalInboxCrypto.SLOT_ENTRY_SIZE, maxEntry.size)
        assertArrayEquals(max, JournalInboxCrypto.openSlot(maxEntry, 23, keys[23].privateKey))
    }

    @Test
    fun sealToSlots_wrongKeyCountOrOversizePayload_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            JournalInboxCrypto.sealToSlots(payload, publicKeys.drop(1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            JournalInboxCrypto.sealToSlots(ByteArray(JournalInboxCrypto.PAYLOAD_SIZE + 1), publicKeys)
        }
    }

    @Test
    fun openSingle_ownKey_returnsThePayloadAndForeignKeyReturnsNull() {
        val entry = JournalInboxCrypto.sealTo(payload, keys[0].publicKey)

        assertEquals(JournalInboxCrypto.SINGLE_ENTRY_SIZE, entry.size)
        assertArrayEquals(payload, JournalInboxCrypto.openSingle(entry, keys[0].privateKey))
        assertNull(JournalInboxCrypto.openSingle(entry, keys[1].privateKey))
        assertNull(JournalInboxCrypto.openSingle(Random.nextBytes(entry.size), keys[0].privateKey))
    }

    @Test
    fun sealTo_emptyAndMaxPayload_haveTheSameConstantLength() {
        for (size in listOf(0, JournalInboxCrypto.PAYLOAD_SIZE)) {
            val entry = JournalInboxCrypto.sealTo(ByteArray(size), keys[0].publicKey)
            assertEquals(JournalInboxCrypto.SINGLE_ENTRY_SIZE, entry.size)
        }
    }

    @Test
    fun openSingleAndOpenSlot_entryOfTheOtherRing_returnNull() {
        val single = JournalInboxCrypto.sealTo(payload, keys[0].publicKey)
        val slots = JournalInboxCrypto.sealToSlots(payload, publicKeys)

        assertNull(JournalInboxCrypto.openSingle(slots, keys[0].privateKey))
        assertNull(JournalInboxCrypto.openSlot(single, 0, keys[0].privateKey))
    }

    @Test
    fun decode_encodedPayload_returnsTheSameFields() {
        for (photo in listOf(null, ByteArray(0), Random.nextBytes(InboxPayload.MAX_PHOTO_SIZE))) {
            val decoded = checkNotNull(InboxPayload.decode(InboxPayload(42, -5, photo).encode()))

            assertEquals(42, decoded.seq)
            assertEquals(-5, decoded.timestamp)
            assertEquals(photo?.toList(), decoded.photo?.toList())
        }
    }

    @Test
    fun inboxPayload_oversizePhoto_isRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            InboxPayload(1, 1, ByteArray(InboxPayload.MAX_PHOTO_SIZE + 1))
        }
    }

    @Test
    fun decode_malformedBytes_returnsNull() {
        val valid = InboxPayload(1, 1, byteArrayOf(1, 2)).encode()
        val oversizeLength = valid.copyOf().also { it[16] = 0x7F }

        assertNull(InboxPayload.decode(ByteArray(19)))
        assertNull(InboxPayload.decode(valid + 0))
        assertNull(InboxPayload.decode(valid.copyOf(valid.size - 1)))
        assertNull(InboxPayload.decode(oversizeLength))
        assertNull(InboxPayload.decode(InboxPayload(1, 1, null).encode() + 0))
    }

    private companion object {
        const val WRAP_SIZE = AesGcm.NONCE_SIZE + JournalInboxCrypto.KEY_SIZE + AesGcm.TAG_SIZE
    }
}
