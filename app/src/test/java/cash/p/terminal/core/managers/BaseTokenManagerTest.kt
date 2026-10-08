package cash.p.terminal.core.managers

import cash.p.terminal.core.ICoinManager
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.TokenQuery
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import kotlin.test.assertEquals

class BaseTokenManagerTest {

    private val bitcoin = token("bitcoin")
    private val ethereum = token("ethereum")

    private val coinManager = mockk<ICoinManager> {
        every { getToken(any()) } answers {
            when (firstArg<TokenQuery>().blockchainType) {
                BlockchainType.Bitcoin -> bitcoin
                BlockchainType.Ethereum -> ethereum
                else -> null
            }
        }
    }
    private val localStorage = mockk<ILocalStorage>(relaxed = true)

    @Test
    fun constructor_doesNotQueryCatalog() {
        BaseTokenManager(coinManager, localStorage)

        verify(exactly = 0) { coinManager.getToken(any()) }
    }

    @Test
    fun token_balanceTotalCoinUidMatches_returnsStoredToken() {
        every { localStorage.balanceTotalCoinUid } returns "ethereum"
        val manager = BaseTokenManager(coinManager, localStorage)

        assertEquals(ethereum, manager.token)
        assertEquals(ethereum, manager.baseTokenFlow.value)
    }

    @Test
    fun token_noStoredUid_returnsFirstToken() {
        every { localStorage.balanceTotalCoinUid } returns null
        val manager = BaseTokenManager(coinManager, localStorage)

        assertEquals(bitcoin, manager.token)
        assertEquals(bitcoin, manager.baseTokenFlow.value)
    }

    @Test
    fun toggleBaseToken_emitsNextTokenAndPersistsUid() {
        every { localStorage.balanceTotalCoinUid } returns null
        val manager = BaseTokenManager(coinManager, localStorage)

        manager.toggleBaseToken()

        assertEquals(ethereum, manager.baseTokenFlow.value)
        assertEquals(ethereum, manager.token)
        verify { localStorage.balanceTotalCoinUid = "ethereum" }
    }

    private fun token(coinUid: String) = mockk<Token> {
        every { coin.uid } returns coinUid
    }
}
