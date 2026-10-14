package cash.p.terminal.wallet

import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import kotlin.test.assertEquals

class ZcashWalletGroupTest {

    @Test
    fun expandedZcashAddressSpecQueries_mnemonic_expandsToFullGroup() {
        assertEquals(
            zcashAddressSpecTokenQueries,
            listOf(shieldedQuery).expandedZcashAddressSpecQueries(mnemonic)
        )
    }

    @Test
    fun expandedZcashAddressSpecQueries_saplingKey_keepsOnlyRequestedSpec() {
        assertEquals(
            listOf(shieldedQuery),
            listOf(shieldedQuery).expandedZcashAddressSpecQueries(saplingKey)
        )
    }

    @Test
    fun expandedZcashAddressSpecQueries_partialUfvk_keepsOnlyRequestedSpecs() {
        val queries = listOf(shieldedQuery, transparentQuery)

        assertEquals(queries, queries.expandedZcashAddressSpecQueries(ufvKey))
    }

    @Test
    fun expandedZcashAddressSpecQueries_saplingKey_keepsNonZcashQueries() {
        val bitcoinQuery = TokenQuery(BlockchainType.Bitcoin, TokenType.Native)

        assertEquals(
            listOf(bitcoinQuery, shieldedQuery),
            listOf(bitcoinQuery, shieldedQuery).expandedZcashAddressSpecQueries(saplingKey)
        )
    }

    @Test
    fun normalizedZcashWalletQueriesForLoad_mnemonic_expandsStoredSpec() {
        assertEquals(
            zcashAddressSpecTokenQueries,
            listOf(shieldedQuery).normalizedZcashWalletQueriesForLoad(mnemonic)
        )
    }

    @Test
    fun normalizedZcashWalletQueriesForLoad_saplingKey_doesNotExpandStoredSpec() {
        assertEquals(
            listOf(shieldedQuery),
            listOf(shieldedQuery).normalizedZcashWalletQueriesForLoad(saplingKey)
        )
    }

    @Test
    fun normalizedZcashWalletQueriesForLoad_legacyNativeOnUfvk_stillMigratesToFullGroup() {
        assertEquals(
            zcashAddressSpecTokenQueries,
            listOf(zcashLegacyNativeTokenQuery).normalizedZcashWalletQueriesForLoad(ufvKey)
        )
    }

    @Test
    fun expandedZcashAddressSpecTokens_mnemonic_expandsToFullGroup() {
        assertEquals(
            zcashTokens,
            listOf(shieldedToken).expandedZcashAddressSpecTokens(marketKit, mnemonic)
        )
    }

    @Test
    fun expandedZcashAddressSpecTokens_saplingKey_keepsOnlyRequestedToken() {
        assertEquals(
            listOf(shieldedToken),
            listOf(shieldedToken).expandedZcashAddressSpecTokens(marketKit, saplingKey)
        )
    }

    private companion object {
        val mnemonic = AccountType.Mnemonic(List(12) { "abandon" }, "")
        val saplingKey = AccountType.ZCashSaplingKey("secret-extended-key-main1q")
        val ufvKey = AccountType.ZCashUfvKey("uview1q")

        val shieldedQuery = zcashQuery(TokenType.AddressSpecType.Shielded)
        val transparentQuery = zcashQuery(TokenType.AddressSpecType.Transparent)

        val zcashTokens = TokenType.AddressSpecType.entries.map {
            Token(
                coin = Coin(uid = "zcash", name = "Zcash", code = "ZEC"),
                blockchain = Blockchain(BlockchainType.Zcash, "Zcash", null),
                type = TokenType.AddressSpecTyped(it),
                decimals = 8
            )
        }
        val shieldedToken = zcashTokens.first { it.tokenQuery == shieldedQuery }

        val marketKit = mockk<MarketKitWrapper> {
            every { tokens(any<List<TokenQuery>>()) } answers {
                zcashTokens.filter { it.tokenQuery in firstArg<List<TokenQuery>>() }
            }
        }

        private fun zcashQuery(spec: TokenType.AddressSpecType) =
            TokenQuery(BlockchainType.Zcash, TokenType.AddressSpecTyped(spec))
    }
}
