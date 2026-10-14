package cash.p.terminal.modules.send

import cash.p.terminal.resources.Hud_Text_NoInternet
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.trezor_connect_failed
import cash.p.terminal.resources.trezor_not_initialized_description
import com.piratecash.monero.signer.HardwareWalletErrorCode
import com.piratecash.monero.signer.HardwareWalletOperationException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
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

    private fun hardwareError(error: HardwareWalletErrorCode) =
        HardwareWalletOperationException(error, null)
}
