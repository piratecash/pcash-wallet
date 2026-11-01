// SLIP-39 Shamir secret sharing, ported from trezor/python-shamir-mnemonic (MIT, Copyright (c) 2018 Andrew R. Kozlik).
package cash.p.terminal.wallet.slip39

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Messages never carry share words or secret bytes. */
sealed class Slip39Exception(message: String) : Exception(message) {
    class InvalidWord(val index: Int) : Slip39Exception("Invalid SLIP-39 word at position $index")
    class InvalidLength : Slip39Exception("Unsupported SLIP-39 size")
    class InvalidChecksum : Slip39Exception("Bad SLIP-39 checksum")
    class InvalidPadding : Slip39Exception("Bad SLIP-39 padding")
    class InvalidGroupThreshold : Slip39Exception("SLIP-39 GT exceeds G")
    class MismatchedSet : Slip39Exception("SLIP-39 shares differ in metadata")
    class DuplicateShare : Slip39Exception("Repeated SLIP-39 share")
    class InsufficientShares : Slip39Exception("Not enough SLIP-39 shares")
    class InvalidDigest : Slip39Exception("SLIP-39 digest mismatch")
}

object Slip39Shamir {

    fun parseShare(mnemonic: String): Slip39Share = Slip39Share.decode(mnemonic)

    /** The spec allows printable ASCII only. */
    fun isValidPassphrase(passphrase: String): Boolean = passphrase.all { it.code in PRINTABLE_ASCII }

    /** Expects exactly the shares needed (see [Slip39RecoveryState.selectedForCombine]); surplus is a caller bug. */
    fun combine(shares: List<Slip39Share>): EncryptedMasterSecret {
        val first = shares.firstOrNull() ?: throw Slip39Exception.InsufficientShares()
        val groups = groupedSet(shares)
        if (groups.size < first.groupThreshold) throw Slip39Exception.InsufficientShares()
        require(groups.size == first.groupThreshold) { "More groups than the group threshold" }
        val groupSecrets = groups.map { (groupIndex, members) -> RawShare(groupIndex, recoverGroupSecret(members)) }
        return EncryptedMasterSecret(
            identifier = first.identifier,
            extendable = first.extendable,
            iterationExponent = first.iterationExponent,
            ciphertext = recoverSecret(first.groupThreshold, groupSecrets),
        )
    }

    /** Single group, [threshold]-of-[count] members; threshold 1 yields deterministic copies of [ems]. */
    fun split(ems: EncryptedMasterSecret, threshold: Int, count: Int, random: SecureRandom): List<List<String>> {
        require(Slip39Share.wordCountFor(ems.ciphertext.size) in Slip39Share.SUPPORTED_WORD_COUNTS) {
            "Unsupported secret size"
        }
        require(threshold > 1 || count == 1) { "Threshold 1 allows a single share only" }
        return splitSecret(threshold, count, ems.ciphertext, random).map { share ->
            Slip39Share(
                identifier = ems.identifier,
                extendable = ems.extendable,
                iterationExponent = ems.iterationExponent,
                groupIndex = 0,
                groupThreshold = 1,
                groupCount = 1,
                memberIndex = share.x,
                memberThreshold = threshold,
                value = share.data,
            ).words()
        }
    }

    internal fun recoverGroupSecret(members: List<Slip39Share>): ByteArray {
        val threshold = members.first().memberThreshold
        if (members.size < threshold) throw Slip39Exception.InsufficientShares()
        require(members.size == threshold) { "More members than the member threshold" }
        return recoverSecret(threshold, members.map { RawShare(it.memberIndex, it.value) })
    }

    /** Groups [shares] by group index; throws when they cannot all belong to one set. */
    internal fun groupedSet(shares: List<Slip39Share>): Map<Int, List<Slip39Share>> {
        val groups = shares.groupBy { it.groupIndex }
        val sameSet = shares.all { it.belongsToSameSet(shares.first()) } &&
            groups.values.all { members -> members.all { it.memberThreshold == members.first().memberThreshold } }
        if (!sameSet) throw Slip39Exception.MismatchedSet()
        if (groups.values.any { members -> members.distinctBy { it.memberIndex }.size != members.size }) {
            throw Slip39Exception.DuplicateShare()
        }
        return groups
    }

    private fun splitSecret(threshold: Int, count: Int, secret: ByteArray, random: SecureRandom): List<RawShare> {
        require(threshold in 1..count && count <= MAX_SHARE_COUNT) { "Invalid sharing scheme" }
        if (threshold == 1) return List(count) { RawShare(it, secret) }

        val randomShares = List(threshold - 2) { RawShare(it, random.bytes(secret.size)) }
        val randomPart = random.bytes(secret.size - DIGEST_LENGTH_BYTES)
        val baseShares = randomShares +
            RawShare(DIGEST_INDEX, createDigest(randomPart, secret) + randomPart) +
            RawShare(SECRET_INDEX, secret)
        return randomShares + (randomShares.size until count).map { RawShare(it, interpolate(baseShares, it)) }
    }

    private fun recoverSecret(threshold: Int, shares: List<RawShare>): ByteArray {
        if (threshold == 1) return shares.first().data

        val secret = interpolate(shares, SECRET_INDEX)
        val digestShare = interpolate(shares, DIGEST_INDEX)
        val expectedDigest = createDigest(digestShare.copyOfRange(DIGEST_LENGTH_BYTES, digestShare.size), secret)
        if (!MessageDigest.isEqual(digestShare.copyOf(DIGEST_LENGTH_BYTES), expectedDigest)) {
            throw Slip39Exception.InvalidDigest()
        }
        return secret
    }

    private fun createDigest(randomPart: ByteArray, secret: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(randomPart, mac.algorithm))
        return mac.doFinal(secret).copyOf(DIGEST_LENGTH_BYTES)
    }

    /** Lagrange interpolation over GF(256), evaluated at [x]. */
    private fun interpolate(shares: List<RawShare>, x: Int): ByteArray {
        shares.firstOrNull { it.x == x }?.let { return it.data }

        val logProduct = shares.sumOf { Gf256.log[it.x xor x] }
        val result = ByteArray(shares.first().data.size)
        shares.forEach { share ->
            val logBasis = (logProduct - Gf256.log[share.x xor x] - shares.sumOf { Gf256.log[share.x xor it.x] })
                .mod(Gf256.ORDER)
            share.data.forEachIndexed { i, byte ->
                val value = byte.toInt() and 0xFF
                if (value != 0) {
                    val term = Gf256.exp[(Gf256.log[value] + logBasis) % Gf256.ORDER]
                    result[i] = (result[i].toInt() xor term).toByte()
                }
            }
        }
        return result
    }

    private fun SecureRandom.bytes(size: Int) = ByteArray(size).also(this::nextBytes)

    private class RawShare(val x: Int, val data: ByteArray)

    private object Gf256 {
        const val ORDER = 255
        val exp = IntArray(ORDER)
        val log = IntArray(ORDER + 1)

        init {
            var poly = 1
            for (i in 0 until ORDER) {
                exp[i] = poly
                log[poly] = i
                poly = (poly shl 1) xor poly
                if (poly and 0x100 != 0) poly = poly xor 0x11B
            }
        }
    }

    private const val MAX_SHARE_COUNT = 16
    private const val DIGEST_LENGTH_BYTES = 4
    private const val DIGEST_INDEX = 254
    private const val SECRET_INDEX = 255
    private val PRINTABLE_ASCII = 32..126
}
