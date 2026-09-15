package cash.p.terminal.ui.compose.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The readability cap belongs only to callers that can fall back to the animated tape.
 * Screens without a fallback (the encrypted recovery-phrase QR, the receive address) must keep
 * rendering anything the encoder accepts.
 */
class PcashQrCodePredicatesTest {

    @Test
    fun isReadablePcashQrCode_smallContent_returnsTrue() {
        assertTrue(isReadablePcashQrCode("pcash:tx:v1:bitcoin:body"))
    }

    @Test
    fun isReadablePcashQrCode_atReadabilityLimit_returnsTrue() {
        assertTrue(isReadablePcashQrCode("a".repeat(MAX_READABLE_QR_PAYLOAD_SIZE)))
    }

    @Test
    fun isReadablePcashQrCode_pastReadabilityBoundary_returnsFalse() {
        assertFalse(isReadablePcashQrCode("a".repeat(MAX_READABLE_QR_PAYLOAD_SIZE + 1)))
    }

    @Test
    fun canEncodeAsPcashQrCode_pastReadabilityLimit_returnsTrue() {
        assertTrue(canEncodeAsPcashQrCode("a".repeat(DENSE_PAYLOAD_SIZE)))
    }

    @Test
    fun canEncodeAsPcashQrCode_pastEncodableLimit_returnsFalse() {
        assertFalse(canEncodeAsPcashQrCode("a".repeat(PcashQrCodeDefaults.MaxEncodableChars + 1)))
    }

    @Test
    fun isReadablePcashQrCode_pastReadabilityLimit_returnsFalse() {
        assertFalse(isReadablePcashQrCode("a".repeat(DENSE_PAYLOAD_SIZE)))
    }

    private companion object {
        /** Longest byte-mode payload that still fits PcashQrCodeDefaults.MaxReadableModules. */
        const val MAX_READABLE_QR_PAYLOAD_SIZE = 661

        /** 121 modules — denser than the cap, well inside what the encoder handles. */
        const val DENSE_PAYLOAD_SIZE = 745
    }
}
