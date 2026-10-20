package cash.p.terminal.core.adapters.zcash

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The reference vector is the librustzcash-produced Sapling extended spending key of the
 * published BIP-39 all-zero-entropy phrase ("abandon … about"), which by definition holds no funds.
 */
private const val REFERENCE_ESK =
    "secret-extended-key-main1q00pkhghqqqqpqpjr7aphsx37860r2y85wfgq66meql6jw69ls69aztxjhq8cmn" +
        "jdhc9v7jnk3utf4g66ddp6cll6fw0vqthr9vnczdjqkxyelkjxgtq2a5g5w6ngqj4rnewvnf3ehh7fzftv4jpkgz" +
        "rtv4jqjej6zdge4gr0se3lftqty8gvymk3097nzt4mdy34ftxea0yfwg84tgmyjckvpngs4zkwfleqwvd9n870zk" +
        "jgt5d5s4uxyqcwsh8t298lgl5vf95g9qdtz6vv"

private const val REFERENCE_ESK_HEX =
    "03de1b5d1700000080321fba1bc0d1f1f4f1a887a392806b5bc83fa93b45fc345e896695c07c6e726df05" +
        "67a53b478b4d51ad35a1d63ffd25cf6017719593c09b2058c4cfed23216057688a3b53402551cf2e64d31" +
        "cdefe4892b65641b20435b2b204b32d09a8cd5037c331fa560590e8613768bcbe98975db491aa566cf5e4" +
        "4b907aad1b24b166066885456727f90398d2ccfe78ad242e8da42bc31018742e75a8a7fa3f4624b4414"

private val NU5_ERA = byteArrayOf(0xB4.toByte(), 0xD0.toByte(), 0xD6.toByte(), 0xC2.toByte())

private fun String.hex() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

private fun component(typecode: Int, value: ByteArray) =
    byteArrayOf(typecode.toByte(), value.size.toByte()) + value

private fun envelope(era: ByteArray, vararg components: ByteArray) =
    components.fold(era) { acc, component -> acc + component }

private fun transparent() = component(0x00, ByteArray(74))
private fun sapling() = component(0x02, REFERENCE_ESK_HEX.hex())
private fun orchard() = component(0x03, ByteArray(32))

class ZcashSaplingSpendingKeyTest {

    @Test
    fun saplingSpendingKey_fullEnvelope_returnsReferenceVector() {
        val usk = envelope(NU5_ERA, transparent(), sapling(), orchard())

        assertEquals(REFERENCE_ESK, saplingSpendingKey(usk))
    }

    @Test
    fun saplingSpendingKey_saplingOnlyEnvelope_returnsReferenceVector() {
        val usk = envelope(NU5_ERA, sapling())

        assertEquals(REFERENCE_ESK, saplingSpendingKey(usk))
    }

    @Test
    fun saplingSpendingKey_foreignEra_returnsNull() {
        val usk = envelope(byteArrayOf(0, 0, 0, 0), sapling())

        assertNull(saplingSpendingKey(usk))
    }

    @Test
    fun saplingSpendingKey_noSaplingComponent_returnsNull() {
        val usk = envelope(NU5_ERA, transparent(), orchard())

        assertNull(saplingSpendingKey(usk))
    }

    @Test
    fun saplingSpendingKey_truncatedComponent_returnsNull() {
        val usk = envelope(NU5_ERA, sapling()).let { it.copyOf(it.size - 1) }

        assertNull(saplingSpendingKey(usk))
    }

    @Test
    fun saplingSpendingKey_multiByteCompactSize_returnsNull() {
        val usk = NU5_ERA + byteArrayOf(0x02, 0xFD.toByte()) + REFERENCE_ESK_HEX.hex()

        assertNull(saplingSpendingKey(usk))
    }

    @Test
    fun saplingSpendingKey_truncatedComponentAfterSapling_returnsNull() {
        val usk = envelope(NU5_ERA, sapling(), orchard()).let { it.copyOf(it.size - 31) }

        assertNull(saplingSpendingKey(usk))
    }

    @Test
    fun saplingSpendingKey_strayTrailingByte_returnsNull() {
        val usk = envelope(NU5_ERA, sapling()) + byteArrayOf(0x03)

        assertNull(saplingSpendingKey(usk))
    }

    @Test
    fun saplingSpendingKey_duplicateSaplingComponent_returnsNull() {
        val usk = envelope(NU5_ERA, sapling(), sapling())

        assertNull(saplingSpendingKey(usk))
    }
}
