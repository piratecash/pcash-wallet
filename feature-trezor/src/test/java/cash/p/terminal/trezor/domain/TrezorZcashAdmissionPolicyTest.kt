package cash.p.terminal.trezor.domain

import cash.p.terminal.trezor.domain.model.TrezorModel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrezorZcashAdmissionPolicyTest {

    @Test
    fun supportsUnifiedAddress_belowMinimumVersion_refused() {
        assertFalse(
            TrezorZcashAdmissionPolicy.supportsUnifiedAddress(TrezorModel.Safe5.ids.single(), "2.5.2"),
        )
    }

    @Test
    fun supportsUnifiedAddress_atOrAboveMinimumVersion_supported() {
        assertTrue(
            TrezorZcashAdmissionPolicy.supportsUnifiedAddress(TrezorModel.Safe5.ids.single(), "2.5.3"),
        )
        assertTrue(
            TrezorZcashAdmissionPolicy.supportsUnifiedAddress(TrezorModel.Safe5.ids.single(), "2.10.0"),
        )
    }

    @Test
    fun supportsUnifiedAddress_atTheAdvertisedMinimum_supported() {
        assertTrue(
            TrezorZcashAdmissionPolicy.supportsUnifiedAddress(
                TrezorModel.Safe5.ids.single(),
                TrezorZcashAdmissionPolicy.MIN_UNIFIED_FIRMWARE_VERSION,
            ),
        )
    }

    @Test
    fun supportsUnifiedAddress_trezorOne_refusedAtEveryVersion() {
        assertFalse(TrezorZcashAdmissionPolicy.supportsUnifiedAddress(TrezorModel.One.ids.single(), "2.5.3"))
        assertFalse(TrezorZcashAdmissionPolicy.supportsUnifiedAddress(TrezorModel.One.ids.single(), "9.9.9"))
    }

    @Test
    fun supportsUnifiedAddress_unparseableFirmware_refused() {
        assertFalse(
            TrezorZcashAdmissionPolicy.supportsUnifiedAddress(TrezorModel.Safe5.ids.single(), "not-a-version"),
        )
    }

    @Test
    fun supportsUnifiedAddress_unknownModel_refused() {
        assertFalse(TrezorZcashAdmissionPolicy.supportsUnifiedAddress("UNKNOWN", "2.10.0"))
    }

    @Test
    fun supportsUnifiedAddress_legacyEmptyMetadata_refused() {
        assertFalse(TrezorZcashAdmissionPolicy.supportsUnifiedAddress("", ""))
    }
}
