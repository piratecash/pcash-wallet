package cash.p.terminal.wallet

import org.junit.Assert.assertEquals
import org.junit.Test

class AccountNameTest {

    @Test
    fun normalizedAccountName_trailingBlankLines_areRemoved() {
        assertEquals("Wallet 11", "Wallet 11\n\n\n".normalizedAccountName())
    }

    @Test
    fun normalizedAccountName_lineBreaksInside_becomeOneSpace() {
        assertEquals("My wallet", "My\n\n\nwallet".normalizedAccountName())
        assertEquals("My wallet", "My\r\nwallet".normalizedAccountName())
        assertEquals("My wallet", "My \n wallet".normalizedAccountName())
    }

    @Test
    fun normalizedAccountName_edgeSpaces_areTrimmed() {
        assertEquals("Wallet 1", "  Wallet 1  ".normalizedAccountName())
    }

    @Test
    fun normalizedAccountName_plainName_isUnchanged() {
        assertEquals("My  wallet (1)", "My  wallet (1)".normalizedAccountName())
    }
}
