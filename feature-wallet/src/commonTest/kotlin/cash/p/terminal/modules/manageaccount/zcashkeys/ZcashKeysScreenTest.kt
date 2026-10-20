package cash.p.terminal.modules.manageaccount.zcashkeys

import cash.p.terminal.resources.Res
import cash.p.terminal.resources.private_keys_zec_sapling_spending_key_error
import cash.p.terminal.resources.private_keys_zec_transparent_key_error
import cash.p.terminal.core.adapters.zcash.ZcashPrivateKeyType
import org.junit.Assert.assertEquals
import org.junit.Test

class ZcashKeysScreenTest {

    @Test
    fun errorMessageRes_transparent_returnsTransparentError() {
        assertEquals(
            Res.string.private_keys_zec_transparent_key_error,
            errorMessageRes(ZcashPrivateKeyType.Transparent)
        )
    }

    @Test
    fun errorMessageRes_shielded_returnsSaplingError() {
        assertEquals(
            Res.string.private_keys_zec_sapling_spending_key_error,
            errorMessageRes(ZcashPrivateKeyType.Shielded)
        )
    }
}
