package cash.p.terminal.core.managers

import cash.p.terminal.wallet.entities.TonWalletVersion
import org.junit.Assert.assertEquals
import org.junit.Test

class TonConnectWalletIdTest {

    @Test
    fun value_eachVersion_roundTripsThroughParse() {
        TonWalletVersion.entries.forEach { version ->
            val walletId = TonConnectWalletId(ACCOUNT_ID, version)

            assertEquals(walletId, TonConnectWalletId.parse(walletId.value))
        }
    }

    @Test
    fun value_eachVersion_keepsStoredFormat() {
        assertEquals(ACCOUNT_ID, TonConnectWalletId(ACCOUNT_ID, TonWalletVersion.V4R2).value)
        assertEquals("$ACCOUNT_ID-w5", TonConnectWalletId(ACCOUNT_ID, TonWalletVersion.W5).value)
    }

    @Test
    fun parse_sessionStoredBeforeW5_isV4r2OfThatAccount() {
        assertEquals(TonConnectWalletId(ACCOUNT_ID, TonWalletVersion.V4R2), TonConnectWalletId.parse(ACCOUNT_ID))
    }

    @Test
    fun allOf_account_listsWalletIdOfEveryVersion() {
        assertEquals(listOf(ACCOUNT_ID, "$ACCOUNT_ID-w5"), TonConnectWalletId.allOf(ACCOUNT_ID))
    }

    private companion object {
        const val ACCOUNT_ID = "3f2b8c1e-5d4a-4b6f-9e7d-1a2b3c4d5e6f"
    }
}
