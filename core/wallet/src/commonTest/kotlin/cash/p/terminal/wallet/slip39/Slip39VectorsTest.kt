package cash.p.terminal.wallet.slip39

import com.google.gson.JsonParser
import io.horizontalsystems.hdwalletkit.HDExtendedKey
import io.horizontalsystems.hdwalletkit.HDWallet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.KClass
import kotlin.test.assertFailsWith

class Slip39VectorsTest {

    @Test
    fun combine_officialValidVectors_decryptToMasterSecretAndRootXprv() {
        val valid = slip39Vectors.filter { it.secretHex.isNotEmpty() }
        assertEquals(15, valid.size)

        valid.forEach { vector ->
            val secret = combine(vector.mnemonics).decrypt("TREZOR")

            assertEquals(vector.name, vector.secretHex, secret.toHexString())
            assertEquals(vector.name, vector.xprv, rootXprv(secret))
        }
    }

    @Test
    fun combine_officialInvalidVectors_throwExpectedTypedErrorWithoutWords() {
        val invalid = slip39Vectors.filter { it.secretHex.isEmpty() }
        assertEquals(EXPECTED_ERRORS.keys, invalid.map { it.number }.toSet())

        invalid.forEach { vector ->
            val error = assertFailsWith<Slip39Exception>(vector.name) { combine(vector.mnemonics) }

            assertEquals(vector.name, EXPECTED_ERRORS.getValue(vector.number), error::class)
            assertTrue(vector.name, error.message.orEmpty().wordlistWords().isEmpty())
        }
    }

    @Test
    fun combine_trezorBasicFixtures_recoverFirmwareSecretAndRootXprv() {
        val basic = combine(BASIC_20_3OF6)
        val extendable = combine(BASIC_EXT_20_2OF3)

        assertEquals("491b795b80fc21ccdf466c0fbc98c8fc", basic.ciphertext.toHexString())
        assertEquals("644c905b0c4da21692f06fff3ed8b3e1", extendable.ciphertext.toHexString())
        assertEquals(BASIC_20_3OF6_XPRV, rootXprv(basic.decrypt("TREZOR")))
        assertEquals(BASIC_EXT_20_2OF3_XPRV, rootXprv(extendable.decrypt("TREZOR")))
        assertEquals(BASIC_33_2OF5_XPRV, rootXprv(combine(BASIC_33_2OF5).decrypt("TREZOR")))
    }

    @Test
    fun combine_trezorAdvancedFixtures_recoverFirmwareSecret() {
        assertEquals("c2d2e26ad06023c60145f150abe2dd2b", combine(ADVANCED_20).ciphertext.toHexString())
        assertEquals(
            "c41d5cf80fed71a008a3a0ae0458ff0c6d621b1a5522bccbfedbcfad87005c06",
            combine(ADVANCED_33).ciphertext.toHexString(),
        )
    }

    @Test
    fun parseShare_trezorSingleShareFixtures_recognisedAsSingleShare() {
        val custom = Slip39Shamir.parseShare(CUSTOM_1OF1)
        val singleExt = Slip39Shamir.parseShare(SINGLE_EXT_20)

        assertTrue(custom.isSingleShare && singleExt.isSingleShare)
        assertEquals(33, custom.wordCount)
        assertEquals(20, singleExt.wordCount)
        assertTrue(singleExt.extendable)
        // Unlike the other fixtures, firmware states this one's master secret, not its EMS.
        assertEquals(
            "491b795b80fc21ccdf466c0fbc98c8fc".encodeToByteArray().toHexString(),
            combine(listOf(CUSTOM_1OF1)).decrypt("").toHexString(),
        )
    }

    private fun rootXprv(seed: ByteArray) = HDExtendedKey(seed, HDWallet.Purpose.BIP44).serializePrivate()

    private fun String.wordlistWords() =
        split(Regex("[^A-Za-z]+")).filter { Slip39Wordlist.indexOf(it.lowercase()) != null }

    internal companion object {
        val EXPECTED_ERRORS: Map<Int, KClass<out Slip39Exception>> = buildMap {
            listOf(2, 21).forEach { put(it, Slip39Exception.InvalidChecksum::class) }
            listOf(3, 22).forEach { put(it, Slip39Exception.InvalidPadding::class) }
            listOf(6, 7, 8, 9, 12, 25, 26, 27, 28, 31).forEach { put(it, Slip39Exception.MismatchedSet::class) }
            listOf(10, 29).forEach { put(it, Slip39Exception.InvalidGroupThreshold::class) }
            listOf(11, 30).forEach { put(it, Slip39Exception.DuplicateShare::class) }
            listOf(13, 32).forEach { put(it, Slip39Exception.InvalidDigest::class) }
            listOf(5, 14, 15, 16, 24, 33, 34, 35).forEach { put(it, Slip39Exception.InsufficientShares::class) }
            listOf(39, 40).forEach { put(it, Slip39Exception.InvalidLength::class) }
        }

        // Trezor firmware fixtures: tests/common.py and test_passphrase_slip39_basic.py (LGPL-3.0 test data).
        val BASIC_20_3OF6 = listOf(
            "extra extend academic bishop cricket bundle tofu goat apart victim enlarge program behavior permit course armed jerky faint language modern",
            "extra extend academic acne away best indicate impact square oasis prospect painting voting guest either argue username racism enemy eclipse",
            "extra extend academic arcade born dive legal hush gross briefing talent drug much home firefly toxic analysis idea umbrella slice",
        )
        val BASIC_EXT_20_2OF3 = listOf(
            "enemy favorite academic acid cowboy phrase havoc level response walnut budget painting inside trash adjust froth kitchen learn tidy punish",
            "enemy favorite academic always academic sniff script carpet romp kind promise scatter center unfair training emphasis evening belong fake enforce",
        )
        val BASIC_33_2OF5 = listOf(
            "hobo romp academic axis august founder knife legal recover alien expect emphasis loan kitchen involve teacher capture rebuild trial numb spider forward ladle lying voter typical security quantity hawk legs idle leaves gasoline",
            "hobo romp academic agency ancestor industry argue sister scene midst graduate profile numb paid headset airport daisy flame express scene usual welcome quick silent downtown oral critical step remove says rhythm venture aunt",
        )
        val ADVANCED_20 = listOf(
            "eraser senior beard romp adorn nuclear spill corner cradle style ancient family general leader ambition exchange unusual garlic promise voice",
            "eraser senior ceramic snake clay various huge numb argue hesitate auction category timber browser greatest hanger petition script leaf pickup",
            "eraser senior ceramic shaft dynamic become junior wrist silver peasant force math alto coal amazing segment yelp velvet image paces",
            "eraser senior ceramic round column hawk trust auction smug shame alive greatest sheriff living perfect corner chest sled fumes adequate",
        )
        val ADVANCED_33 = listOf(
            "wildlife deal beard romp alcohol space mild usual clothes union nuclear testify course research heat listen task location thank hospital slice smell failure fawn helpful priest ambition average recover lecture process dough stadium",
            "wildlife deal acrobat romp anxiety axis starting require metric flexible geology game drove editor edge screw helpful have huge holy making pitch unknown carve holiday numb glasses survive already tenant adapt goat fangs",
        )
        const val CUSTOM_1OF1 =
            "tolerate flexible academic academic average dwarf square home promise aspect temple cluster roster forward hand unfair tenant emperor ceramic element forget perfect knit adapt review usual formal receiver typical pleasure duke yield party"
        const val SINGLE_EXT_20 =
            "academic again academic academic academic academic academic academic academic academic academic academic academic academic academic academic academic pecan provide remember"

        const val BASIC_20_3OF6_XPRV =
            "xprv9s21ZrQH143K2pMWi8jrTawHaj16uKk4CSbvo4Zt61tcrmuUDMx2o1Byzcr3saXNGNvHP8zZgXVdJHsXVdzYFPavxvCyaGyGr1WkAYG83ce"
        const val BASIC_33_2OF5_XPRV =
            "xprv9s21ZrQH143K2o6EXEHpVy8TCYoMmkBnDCCESLdR2ieKwmcNG48ck2XJQY4waS7RUQcXqR9N7HnQbUVEDMWYyREdF1idQqxFHuCfK7fqFni"
        const val BASIC_EXT_20_2OF3_XPRV =
            "xprv9s21ZrQH143K4FS1qQdXYAFVAHiSAnjj21YAKGh2CqUPJ2yQhMmYGT4e5a2tyGLiVsRgTEvajXkxhg92zJ8zmWZas9LguQWz7WZShfJg6RS"
    }
}

internal class Slip39Vector(val name: String, val mnemonics: List<String>, val secretHex: String, val xprv: String) {
    val number: Int get() = name.substringBefore('.').toInt()
}

internal val slip39Vectors: List<Slip39Vector> by lazy {
    JsonParser.parseString(SLIP39_VECTORS_JSON).asJsonArray.map { element ->
        val entry = element.asJsonArray
        Slip39Vector(
            name = entry[0].asString,
            mnemonics = entry[1].asJsonArray.map { it.asString },
            secretHex = entry[2].asString,
            xprv = entry[3].asString,
        )
    }
}

internal fun slip39Vector(number: Int): Slip39Vector = slip39Vectors.first { it.number == number }

internal fun combine(mnemonics: List<String>): EncryptedMasterSecret =
    Slip39Shamir.combine(mnemonics.map(Slip39Shamir::parseShare))
