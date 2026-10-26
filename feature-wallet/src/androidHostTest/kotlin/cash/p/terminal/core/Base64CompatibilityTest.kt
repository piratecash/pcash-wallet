package cash.p.terminal.core

import android.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random
import kotlin.test.assertFailsWith

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Base64CompatibilityTest {

    private val samples = (0..64).map { Random(it).nextBytes(it) }

    @Test
    fun encodeBase64_randomBytes_matchesAndroidNoWrap() {
        samples.forEach { bytes ->
            assertEquals(Base64.encodeToString(bytes, Base64.NO_WRAP), bytes.encodeBase64())
        }
    }

    @Test
    fun encodeBase64Url_randomBytes_matchesAndroidUrlSafeNoPaddingNoWrap() {
        samples.forEach { bytes ->
            assertEquals(Base64.encodeToString(bytes, URL_FLAGS), bytes.encodeBase64Url())
        }
    }

    @Test
    fun decodeBase64Lenient_paddedUnpaddedAndNoisyInput_matchesAndroidDecode() {
        samples.forEach { bytes ->
            decoderInputs(Base64.encodeToString(bytes, Base64.NO_WRAP)).forEach { input ->
                assertArrayEquals(input, Base64.decode(input, Base64.DEFAULT), input.decodeBase64Lenient())
                assertArrayEquals(input, bytes, input.decodeBase64Lenient())
            }
        }
    }

    @Test
    fun decodeBase64UrlLenient_paddedUnpaddedAndNoisyInput_matchesAndroidUrlSafeDecode() {
        samples.forEach { bytes ->
            decoderInputs(Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP)).forEach { input ->
                assertArrayEquals(input, Base64.decode(input, Base64.URL_SAFE), input.decodeBase64UrlLenient())
                assertArrayEquals(input, bytes, input.decodeBase64UrlLenient())
            }
        }
    }

    @Test
    fun decodeBase64Lenient_malformedInput_throwsLikeAndroid() {
        MALFORMED.forEach { input ->
            assertFailsWith<IllegalArgumentException>(input) { Base64.decode(input, Base64.DEFAULT) }
            assertFailsWith<IllegalArgumentException>(input) { input.decodeBase64Lenient() }
        }
    }

    @Test
    fun decodeBase64UrlLenient_malformedInput_throwsLikeAndroid() {
        MALFORMED.forEach { input ->
            assertFailsWith<IllegalArgumentException>(input) { Base64.decode(input, Base64.URL_SAFE) }
            assertFailsWith<IllegalArgumentException>(input) { input.decodeBase64UrlLenient() }
        }
    }

    private fun decoderInputs(padded: String): List<String> {
        val unpadded = padded.trimEnd('=')
        return listOf(padded, unpadded, padded.withNoise(), unpadded.withNoise())
    }

    private fun String.withNoise(): String =
        chunked(4).joinToString(separator = "\n", prefix = " ", postfix = "\r\n") { it.chunked(2).joinToString(" ") }

    private companion object {
        const val URL_FLAGS = Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        val MALFORMED = listOf("A", "AAAAA", "QQ=", "QQ==QQ", "QUJD=", "QQ===")
    }
}
