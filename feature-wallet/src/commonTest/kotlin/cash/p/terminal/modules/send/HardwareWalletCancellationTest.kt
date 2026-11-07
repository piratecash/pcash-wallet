package cash.p.terminal.modules.send

import cash.p.terminal.ledger.domain.LedgerBlindSigningRequiredException
import cash.p.terminal.ledger.domain.LedgerRejectedException
import cash.p.terminal.ledgerkit.apdu.LedgerException
import cash.p.terminal.resources.Hud_Text_NoInternet
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.trezor_connect_failed
import cash.p.terminal.resources.trezor_not_initialized_description
import com.piratecash.monero.signer.HardwareWalletErrorCode
import com.piratecash.monero.signer.HardwareWalletOperationException
import io.horizontalsystems.core.Translator
import io.horizontalsystems.tonkit.core.TonExternalSignerException
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.jetbrains.compose.resources.StringResource
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Test

class HardwareWalletCancellationTest {

    @Test
    fun isHardwareWalletCancelled_moneroCancelled_returnsTrue() {
        val error = HardwareWalletOperationException(
            HardwareWalletErrorCode.Cancelled,
            null,
        )

        assertTrue(error.isHardwareWalletCancelled())
    }

    @Test
    fun isHardwareWalletCancelled_moneroDisconnected_returnsFalse() {
        val error = HardwareWalletOperationException(
            HardwareWalletErrorCode.Disconnected,
            null,
        )

        assertFalse(error.isHardwareWalletCancelled())
    }

    @Test
    fun userMessageRes_knownErrors_returnsSpecificMessage() {
        assertEquals(
            Res.string.trezor_not_initialized_description,
            hardwareError(HardwareWalletErrorCode.DeviceNotInitialized).userMessageRes(),
        )
        assertEquals(
            Res.string.Hud_Text_NoInternet,
            hardwareError(HardwareWalletErrorCode.Network).userMessageRes(),
        )
        assertEquals(
            Res.string.trezor_connect_failed,
            hardwareError(HardwareWalletErrorCode.Disconnected).userMessageRes(),
        )
    }

    @Test
    fun hardwareWalletUserMessageRes_nonHardwareError_returnsConnectionMessage() {
        assertEquals(
            Res.string.trezor_connect_failed,
            IllegalStateException().hardwareWalletUserMessageRes(),
        )
    }

    @Test
    fun signingErrorText_ledgerRejected_isSilent() {
        assertNull(LedgerRejectedException().signingErrorText(FALLBACK))
    }

    @Test
    fun signingErrorText_ledgerBlindSigningOff_showsInstruction() {
        stubTranslatorWithKeys()
        val text = LedgerBlindSigningRequiredException("Ethereum").signingErrorText(FALLBACK)

        assertEquals("ledger_blind_signing_required", text)
    }

    @Test
    fun signingErrorText_ledgerTronSettingOff_showsTronSettings() {
        stubTranslatorWithKeys()
        val text = LedgerBlindSigningRequiredException("Tron").signingErrorText(FALLBACK)

        assertEquals("ledger_tron_settings_required", text)
    }

    @Test
    fun signingErrorText_ledgerTonBlindSigningOff_showsTonSwitch() {
        stubTranslatorWithKeys()
        val text = LedgerBlindSigningRequiredException("TON").signingErrorText(FALLBACK)

        assertEquals("ledger_ton_blind_signing_required", text)
    }

    @Test
    fun signingErrorText_ledgerDataTooLarge_showsSizeLimit() {
        stubTranslatorWithKeys()

        assertEquals("ledger_data_too_large", LedgerException.DataTooLarge().signingErrorText(FALLBACK))
    }

    @Test
    fun signingErrorText_ledgerKeyDerivationLimit_asksToReopenZcashApp() {
        stubTranslatorWithKeys()

        assertEquals("ledger_zcash_reopen_app", LedgerException.KeyDerivationLimit().signingErrorText(FALLBACK))
    }

    @Test
    fun hardwareWalletErrorMessage_tonMultipleMessages_showsSingleMessageLimit() {
        stubTranslatorWithKeys()

        assertEquals(
            "ledger_ton_single_message",
            TonExternalSignerException.UnsupportedMessageCount(2).hardwareWalletErrorMessage(),
        )
    }

    @Test
    fun hardwareWalletErrorMessage_tonSignedHashMismatch_showsSigningFailed() {
        stubTranslatorWithKeys()

        val message = TonExternalSignerException.SignedHashMismatch().hardwareWalletErrorMessage()

        assertEquals("ledger_signing_failed", message)
    }

    @Test
    fun hardwareWalletCaution_nonHardwareError_returnsNull() {
        assertNull(IllegalStateException().hardwareWalletCaution())
    }

    @Test
    fun signingErrorText_nonHardwareError_usesFallback() {
        assertEquals(FALLBACK, IllegalStateException().signingErrorText(FALLBACK))
    }

    private fun stubTranslatorWithKeys() {
        mockkObject(Translator)
        every { Translator.getString(any(), *anyVararg()) } answers { firstArg<StringResource>().key }
    }

    @After
    fun tearDown() {
        unmockkObject(Translator)
    }

    private fun hardwareError(error: HardwareWalletErrorCode) =
        HardwareWalletOperationException(error, null)

    private companion object {
        const val FALLBACK = "fallback"
    }
}
