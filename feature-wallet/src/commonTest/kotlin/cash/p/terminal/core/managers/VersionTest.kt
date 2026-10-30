package cash.p.terminal.core.managers

import kotlin.test.Test
import kotlin.test.assertEquals

class VersionTest {

    //0 is equal
    //-1 is less
    //1 is greater

    @Test
    fun testVersionCompare() {
        compare("1.0.0", "0.21.1", 1)
        compare("0.33.0", "1.2.1", -1)
        compare("0.21.0", "0.21.1", -1)
        compare("0.22.0", "1.21.1", -1)
        compare("0.22.0", "0.21.1", 1)
        compare("0.22", "0.21.1", 1)
        compare("0.22", "0.21", 1)
        compare("1", "0.0.1", 1)
    }

    private fun compare(version1: String, version2: String, expected: Int) {
        assertEquals(expected, Version(version1).compareTo(Version(version2)))
    }
}
