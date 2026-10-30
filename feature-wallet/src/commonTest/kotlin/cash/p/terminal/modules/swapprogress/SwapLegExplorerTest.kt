package cash.p.terminal.modules.swapprogress

import cash.p.terminal.core.ITransactionsAdapter
import cash.p.terminal.core.managers.TransactionAdapterManager
import cash.p.terminal.wallet.transaction.TransactionSource
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SwapLegExplorerTest {
    private val adapter = mockk<ITransactionsAdapter> {
        every { explorerTitle } returns "Etherscan"
        every { getTransactionUrl("0xabc") } returns "https://etherscan.io/tx/0xabc"
        every { getTransactionUrl("blank") } returns ""
    }
    private val source = mockk<TransactionSource> {
        every { blockchain } returns Blockchain(BlockchainType.Ethereum, "Ethereum", null)
        every { account } returns mockk { every { id } returns "account-1" }
    }
    private val manager = mockk<TransactionAdapterManager> {
        every { adaptersReadyFlow } returns MutableStateFlow(mapOf(source to adapter))
    }
    private val explorer = SwapLegExplorer(manager)

    @Test
    fun explorer_matchingChainAndAccount_returnsAdapterExplorer() {
        val result = explorer.explorer("1", "account-1", "0xabc")

        assertEquals("Etherscan", result?.title)
        assertEquals("https://etherscan.io/tx/0xabc", result?.url)
    }

    @Test
    fun explorer_otherAccount_returnsNull() {
        assertNull(explorer.explorer("1", "account-2", "0xabc"))
    }

    @Test
    fun explorer_chainWithoutBlockchainType_returnsNull() {
        assertNull(explorer.explorer("near", "account-1", "0xabc"))
    }

    @Test
    fun explorer_blankUrl_returnsNull() {
        assertNull(explorer.explorer("1", "account-1", "blank"))
    }
}
