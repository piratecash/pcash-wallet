package cash.p.terminal.vault

import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class InboxKeyPair(val privateKey: ByteArray, val publicKey: ByteArray)

/** One mailbox record. There is deliberately no field for the typed password. */
class InboxPayload(val seq: Long, val timestamp: Long, val photo: ByteArray?) {
    init {
        require(photo == null || photo.size <= MAX_PHOTO_SIZE) { "Photo exceeds $MAX_PHOTO_SIZE bytes" }
    }

    fun encode(): ByteArray {
        val photoBytes = photo ?: ByteArray(0)
        return ByteBuffer.allocate(HEADER_SIZE + photoBytes.size)
            .putLong(seq)
            .putLong(timestamp)
            .putInt(photo?.size ?: NO_PHOTO)
            .put(photoBytes)
            .array()
    }

    companion object {
        const val MAX_PHOTO_SIZE = 196_608
        private const val NO_PHOTO = -1
        private const val HEADER_SIZE = 2 * Long.SIZE_BYTES + Int.SIZE_BYTES
        const val MAX_ENCODED_SIZE = HEADER_SIZE + MAX_PHOTO_SIZE

        fun decode(bytes: ByteArray): InboxPayload? {
            if (bytes.size < HEADER_SIZE) return null
            val buffer = ByteBuffer.wrap(bytes)
            val seq = buffer.long
            val timestamp = buffer.long
            val photoSize = buffer.int
            val photo = when {
                photoSize == NO_PHOTO && !buffer.hasRemaining() -> null
                photoSize in 0..MAX_PHOTO_SIZE && buffer.remaining() == photoSize ->
                    ByteArray(photoSize).also(buffer::get)
                else -> return null
            }
            return InboxPayload(seq, timestamp, photo)
        }
    }
}

/**
 * Entry layout: `[ephemeral public key][one wrap per recipient][body]`. A wrap is the random content key under
 * HKDF(X25519(ephemeral, recipient)); the body is the payload, padded to a fixed size, under the content key.
 */
object JournalInboxCrypto {
    const val KEY_SIZE = 32
    const val SLOT_COUNT = PasswordVault.SLOT_COUNT
    const val PAYLOAD_SIZE = InboxPayload.MAX_ENCODED_SIZE
    private const val WRAP_SIZE = AesGcm.NONCE_SIZE + KEY_SIZE + AesGcm.TAG_SIZE
    private const val PADDED_SIZE = Int.SIZE_BYTES + PAYLOAD_SIZE
    private const val BODY_SIZE = AesGcm.NONCE_SIZE + PADDED_SIZE + AesGcm.TAG_SIZE
    const val SLOT_ENTRY_SIZE = KEY_SIZE + SLOT_COUNT * WRAP_SIZE + BODY_SIZE
    const val SINGLE_ENTRY_SIZE = KEY_SIZE + WRAP_SIZE + BODY_SIZE

    private val INBOX_INFO = "pcash-login-inbox-v1".toByteArray(Charsets.US_ASCII)
    private val NOTIFY_INFO = "pcash-login-notify-v1".toByteArray(Charsets.US_ASCII)
    private val secureRandom = SecureRandom()

    fun generateKeyPair(): InboxKeyPair {
        val privateKey = X25519PrivateKeyParameters(secureRandom)
        return InboxKeyPair(privateKey.encoded, privateKey.generatePublicKey().encoded)
    }

    fun publicKey(privateKey: ByteArray): ByteArray =
        X25519PrivateKeyParameters(privateKey, 0).generatePublicKey().encoded

    /** A real curve point whose private half is dropped: random bytes would be distinguishable from a key. */
    fun decoyPublicKey(): ByteArray = generateKeyPair().let {
        it.privateKey.fill(0)
        it.publicKey
    }

    fun sealToSlots(payload: ByteArray, publicKeys: List<ByteArray>): ByteArray {
        require(publicKeys.size == SLOT_COUNT) { "Exactly $SLOT_COUNT public keys are required" }
        return seal(payload, publicKeys.mapIndexed { index, key -> Recipient(key, slotInfo(index)) })
    }

    fun sealTo(payload: ByteArray, publicKey: ByteArray): ByteArray =
        seal(payload, listOf(Recipient(publicKey, NOTIFY_INFO)))

    /** Null for a foreign key, a decoy, random or tampered bytes. */
    fun openSlot(entry: ByteArray, index: Int, privateKey: ByteArray): ByteArray? =
        if (entry.size != SLOT_ENTRY_SIZE || index !in 0 until SLOT_COUNT) {
            null
        } else {
            open(entry, index, SLOT_COUNT, slotInfo(index), privateKey)
        }

    /** Null for a foreign key, a decoy, random or tampered bytes. */
    fun openSingle(entry: ByteArray, privateKey: ByteArray): ByteArray? =
        if (entry.size != SINGLE_ENTRY_SIZE) null else open(entry, 0, 1, NOTIFY_INFO, privateKey)

    private fun seal(payload: ByteArray, recipients: List<Recipient>): ByteArray {
        require(payload.size <= PAYLOAD_SIZE) { "Payload exceeds $PAYLOAD_SIZE bytes" }
        val ephemeral = X25519PrivateKeyParameters(secureRandom)
        val ephemeralPublic = ephemeral.generatePublicKey().encoded
        val contentKey = ByteArray(KEY_SIZE).also(secureRandom::nextBytes)
        val padded = ByteBuffer.allocate(PADDED_SIZE).putInt(payload.size).put(payload).array()
        try {
            val entry = ByteBuffer.allocate(KEY_SIZE + recipients.size * WRAP_SIZE + BODY_SIZE).put(ephemeralPublic)
            recipients.forEach { recipient ->
                val salt = ephemeralPublic + recipient.publicKey
                val wrapKey = requireNotNull(wrapKey(ephemeral, recipient.publicKey, salt, recipient.info)) {
                    "Recipient key is not a usable X25519 public key"
                }
                entry.put(AesGcm.encrypt(contentKey, wrapKey))
                wrapKey.fill(0)
            }
            return entry.put(AesGcm.encrypt(padded, contentKey)).array()
        } finally {
            contentKey.fill(0)
            padded.fill(0)
        }
    }

    private fun open(
        entry: ByteArray,
        wrapIndex: Int,
        wrapCount: Int,
        info: ByteArray,
        privateKey: ByteArray,
    ): ByteArray? {
        if (privateKey.size != KEY_SIZE) return null
        val recipient = X25519PrivateKeyParameters(privateKey, 0)
        val ephemeralPublic = entry.copyOfRange(0, KEY_SIZE)
        val salt = ephemeralPublic + recipient.generatePublicKey().encoded
        val wrapKey = wrapKey(recipient, ephemeralPublic, salt, info) ?: return null
        val wrapStart = KEY_SIZE + wrapIndex * WRAP_SIZE
        val contentKey = AesGcm.decrypt(entry.copyOfRange(wrapStart, wrapStart + WRAP_SIZE), wrapKey)
        wrapKey.fill(0)
        contentKey ?: return null
        val padded = AesGcm.decrypt(entry.copyOfRange(KEY_SIZE + wrapCount * WRAP_SIZE, entry.size), contentKey)
        contentKey.fill(0)
        return padded?.let { unpad(it).also { padded.fill(0) } }
    }

    // A raw X25519 secret is not uniform, so HKDF-Extract runs before the expand-only Hkdf.
    private fun wrapKey(
        own: X25519PrivateKeyParameters,
        peerPublic: ByteArray,
        salt: ByteArray,
        info: ByteArray,
    ): ByteArray? {
        val shared = ByteArray(KEY_SIZE)
        return try {
            own.generateSecret(X25519PublicKeyParameters(peerPublic, 0), shared, 0)
            val prk = hmacSha256(salt, shared)
            Hkdf.expand(prk, info, KEY_SIZE).also { prk.fill(0) }
        } catch (e: IllegalStateException) {
            null
        } finally {
            shared.fill(0)
        }
    }

    private fun unpad(padded: ByteArray): ByteArray? {
        val size = ByteBuffer.wrap(padded).int
        return if (size in 0..PAYLOAD_SIZE) padded.copyOfRange(Int.SIZE_BYTES, Int.SIZE_BYTES + size) else null
    }

    private fun slotInfo(index: Int): ByteArray = INBOX_INFO + index.toByte()

    private class Recipient(val publicKey: ByteArray, val info: ByteArray)
}

internal fun hmacSha256(key: ByteArray, vararg parts: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
    init(SecretKeySpec(key, "HmacSHA256"))
    parts.forEach { update(it) }
    doFinal()
}
