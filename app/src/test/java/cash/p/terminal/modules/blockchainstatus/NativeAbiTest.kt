package cash.p.terminal.modules.blockchainstatus

import org.junit.Assert.assertEquals
import org.junit.Test

class NativeAbiTest {

    @Test
    fun nativeAbiLabel_arm64OnArm64_noMarker() {
        assertEquals(
            "arm64-v8a",
            nativeAbiLabel("/data/app/x/lib/arm64", listOf("arm64-v8a", "armeabi-v7a"))
        )
    }

    @Test
    fun nativeAbiLabel_arm32OnArm64Device_noMarker() {
        assertEquals(
            "armeabi-v7a",
            nativeAbiLabel("/data/app/x/lib/arm", listOf("arm64-v8a", "armeabi-v7a"))
        )
    }

    @Test
    fun nativeAbiLabel_arm64OnX86FirstDevice_marksTranslation() {
        assertEquals(
            "arm64-v8a (translated on x86_64)",
            nativeAbiLabel("/data/app/x/lib/arm64", listOf("x86_64", "arm64-v8a"))
        )
    }

    @Test
    fun nativeAbiLabel_x86SuffixesOnX86Device_noMarker() {
        assertEquals("x86_64", nativeAbiLabel("/lib/x86_64", listOf("x86_64", "x86")))
        assertEquals("x86", nativeAbiLabel("/lib/x86", listOf("x86_64", "x86")))
    }

    @Test
    fun nativeAbiLabel_unknownSuffix_returnsRawWithoutMarker() {
        assertEquals("mips", nativeAbiLabel("/data/app/x/lib/mips", listOf("arm64-v8a")))
    }
}
