package cash.p.terminal.modules.walletconnect

import com.reown.walletkit.client.Wallet
import com.reown.walletkit.client.Wallet.Model.Validation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WCVerifyAlertTest {

    @Test
    fun toAlert_scamWithInvalidOrigin_returnsScam() {
        assertEquals(WCVerifyAlert.Scam, context(Validation.INVALID, isScam = true).toAlert(reportUnverified = true))
    }

    @Test
    fun toAlert_scamWithValidOrigin_returnsScam() {
        assertEquals(WCVerifyAlert.Scam, context(Validation.VALID, isScam = true).toAlert(reportUnverified = false))
    }

    @Test
    fun toAlert_invalidWithOrigin_returnsImpersonationWithOrigin() {
        assertEquals(
            WCVerifyAlert.Impersonation(ORIGIN),
            context(Validation.INVALID, origin = ORIGIN).toAlert(reportUnverified = false)
        )
    }

    @Test
    fun toAlert_invalidWithBlankOrigin_returnsImpersonationWithoutOrigin() {
        assertEquals(
            WCVerifyAlert.Impersonation(null),
            context(Validation.INVALID, origin = " ").toAlert(reportUnverified = false)
        )
    }

    @Test
    fun toAlert_unknownReportUnverified_returnsUnverified() {
        assertEquals(WCVerifyAlert.Unverified, context(Validation.UNKNOWN).toAlert(reportUnverified = true))
    }

    @Test
    fun toAlert_unknownNotReportUnverified_returnsNull() {
        assertNull(context(Validation.UNKNOWN).toAlert(reportUnverified = false))
    }

    @Test
    fun toAlert_validNotScam_returnsNull() {
        assertNull(context(Validation.VALID, isScam = false).toAlert(reportUnverified = true))
    }

    @Test
    fun toAlert_validScamUnknown_returnsNull() {
        assertNull(context(Validation.VALID, isScam = null).toAlert(reportUnverified = true))
    }

    private fun context(validation: Validation, origin: String = ORIGIN, isScam: Boolean? = false) =
        Wallet.Model.VerifyContext(
            id = 1L,
            origin = origin,
            validation = validation,
            verifyUrl = "https://verify.walletconnect.org",
            isScam = isScam,
        )

    private companion object {
        const val ORIGIN = "https://evil.example"
    }
}
