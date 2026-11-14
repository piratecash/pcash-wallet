package cash.p.terminal.shared.desktop

import kotlin.test.Test
import kotlin.test.assertEquals

class WindowCaptureProtectionTest {

    @Test
    fun applyCaptureProtection_exclusionSupported_excludesTheWindowOnce() {
        val native = RecordingAffinity(supportsExclusion = true)

        applyCaptureProtection(native, HWND, protected = true)

        assertEquals(listOf(WDA_EXCLUDEFROMCAPTURE), native.calls)
    }

    @Test
    fun applyCaptureProtection_exclusionUnsupported_fallsBackToMonitor() {
        val native = RecordingAffinity(supportsExclusion = false)

        applyCaptureProtection(native, HWND, protected = true)

        assertEquals(listOf(WDA_EXCLUDEFROMCAPTURE, WDA_MONITOR), native.calls)
    }

    @Test
    fun applyCaptureProtection_unprotected_clearsTheAffinity() {
        val native = RecordingAffinity(supportsExclusion = true)

        applyCaptureProtection(native, HWND, protected = false)

        assertEquals(listOf(WDA_NONE), native.calls)
    }

    private class RecordingAffinity(private val supportsExclusion: Boolean) : DisplayAffinityNative {
        val calls = mutableListOf<Int>()

        override fun setDisplayAffinity(hwnd: Long, affinity: Int): Boolean {
            assertEquals(HWND, hwnd)
            calls += affinity
            return supportsExclusion || affinity != WDA_EXCLUDEFROMCAPTURE
        }
    }

    private companion object {
        const val HWND = 42L
        const val WDA_NONE = 0x0
        const val WDA_MONITOR = 0x1
        const val WDA_EXCLUDEFROMCAPTURE = 0x11
    }
}
