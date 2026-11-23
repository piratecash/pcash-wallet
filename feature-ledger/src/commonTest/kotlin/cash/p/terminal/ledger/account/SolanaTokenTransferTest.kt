package cash.p.terminal.ledger.account

import cash.p.terminal.ledger.account.AccountFixtures.SPL_DESTINATION
import cash.p.terminal.ledger.account.AccountFixtures.SPL_MINT
import cash.p.terminal.ledger.account.AccountFixtures.SPL_OWNER
import cash.p.terminal.ledger.account.AccountFixtures.SPL_RECIPIENT
import cash.p.terminal.ledger.account.AccountFixtures.SPL_SOURCE
import cash.p.terminal.ledger.account.AccountFixtures.solanaMessage
import cash.p.terminal.ledger.account.AccountFixtures.splTransferMessage
import com.solana.core.PublicKey
import com.solana.programs.SystemProgram
import com.solana.programs.TokenProgram
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SolanaTokenTransferTest {

    @Test
    fun parse_transferCheckedToExistingAccount_readsMintAndDestinationWithoutOwner() {
        val transfer = checkNotNull(SolanaTokenTransfer.parse(splTransferMessage()))

        assertEquals(SPL_MINT, transfer.mint)
        assertEquals(SPL_DESTINATION, transfer.destination)
        assertNull(transfer.newAccountOwner)
    }

    @Test
    fun parse_transferCheckedToAccountCreatedInSameMessage_readsItsOwner() {
        val transfer = checkNotNull(SolanaTokenTransfer.parse(splTransferMessage(newRecipient = true)))

        assertEquals(SPL_RECIPIENT, transfer.newAccountOwner)
        assertEquals(SPL_DESTINATION, transfer.destination)
    }

    @Test
    fun parse_deprecatedTransfer_returnsNull() {
        val transfer = TokenProgram.transfer(PublicKey(SPL_SOURCE), PublicKey(SPL_DESTINATION), 1, PublicKey(SPL_OWNER))

        assertNull(SolanaTokenTransfer.parse(solanaMessage(transfer)))
    }

    @Test
    fun parse_solTransfer_returnsNull() {
        val transfer = SystemProgram.transfer(PublicKey(SPL_OWNER), PublicKey(SPL_RECIPIENT), 1)

        assertNull(SolanaTokenTransfer.parse(solanaMessage(transfer)))
    }

    @Test
    fun parse_truncatedMessage_throws() {
        val message = splTransferMessage()

        assertFailsWith<IndexOutOfBoundsException> { SolanaTokenTransfer.parse(message.copyOf(message.size - 4)) }
    }
}
