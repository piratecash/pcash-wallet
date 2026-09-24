package cash.p.terminal.modules.watchaddress

import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.Assert.assertEquals
import org.junit.Test

class WatchAddressServiceTest {

    private val marketKit = mockk<MarketKitWrapper>()
    private val service = WatchAddressService(mockk(), mockk(), mockk(), marketKit, mockk())

    @Test
    fun tokens_thorchainAddress_queriesThorchainNative() {
        assertEquals(
            listOf(TokenQuery(BlockchainType.Thorchain, TokenType.Native)),
            queriedTokens(AccountType.ThorchainAddress("thor1watched")),
        )
    }

    @Test
    fun tokens_mayachainAddress_queriesMayachainNative() {
        assertEquals(
            listOf(TokenQuery(BlockchainType.Mayachain, TokenType.Native)),
            queriedTokens(AccountType.MayachainAddress("maya1watched")),
        )
    }

    private fun queriedTokens(accountType: AccountType): List<TokenQuery> {
        val queries = slot<List<TokenQuery>>()
        every { marketKit.tokens(capture(queries)) } returns emptyList()

        service.tokens(accountType)

        return queries.captured
    }
}
