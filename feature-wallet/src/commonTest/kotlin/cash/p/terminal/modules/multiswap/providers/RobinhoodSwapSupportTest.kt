package cash.p.terminal.modules.multiswap.providers

import cash.p.terminal.network.unstoppable.domain.entity.UnstoppableProviderTokens
import cash.p.terminal.network.unstoppable.domain.entity.UnstoppableToken
import cash.p.terminal.network.unstoppable.domain.repository.UnstoppableRepository
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RobinhoodSwapSupportTest {

    private val nativeToken = token(TokenType.Native)
    private val eip20Token = token(TokenType.Eip20(TOKEN_ADDRESS))

    @Test
    fun supports_robinhoodTokens_uniswapV3AcceptsNativeAndEip20() = runTest {
        assertTrue(UniswapV3Provider.supports(nativeToken))
        assertTrue(UniswapV3Provider.supports(eip20Token))
    }

    @Test
    fun resolve_robinhoodChainIdMode_mapsChainAndBothTokenTypes() = runTest {
        val repository = mockk<UnstoppableRepository> {
            coEvery { getTokens(any()) } returns UnstoppableProviderTokens(
                tokens = emptyList(),
                supportedChainIds = listOf(ROBINHOOD_CHAIN_ID),
            )
        }
        val resolver = UnstoppableTokenResolver(
            UnstoppableProvider.Barter,
            repository,
            mockk(relaxed = true),
        )

        assertTrue(resolver.supports(nativeToken))
        assertEquals(
            UnstoppableTokenResolver.ResolvedAsset(NATIVE_EVM_PLACEHOLDER, ROBINHOOD_CHAIN_ID),
            resolver.resolve(nativeToken),
        )
        assertEquals(
            UnstoppableTokenResolver.ResolvedAsset(TOKEN_ADDRESS, ROBINHOOD_CHAIN_ID),
            resolver.resolve(eip20Token),
        )
        assertEquals(ROBINHOOD_CHAIN_ID, resolver.chainId(BlockchainType.RobinhoodChain))
    }

    @Test
    fun resolve_robinhoodTokenListMode_mapsInboundChainIdToLocalTokens() = runTest {
        val repository = mockk<UnstoppableRepository> {
            coEvery { getTokens(any()) } returns UnstoppableProviderTokens(
                tokens = listOf(
                    UnstoppableToken("robinhood", ROBINHOOD_CHAIN_ID, null, "ETH"),
                    UnstoppableToken("robinhood", ROBINHOOD_CHAIN_ID, TOKEN_ADDRESS, "USDC"),
                ),
                supportedChainIds = emptyList(),
            )
        }
        val marketKit = mockk<MarketKitWrapper> {
            every { token(TokenQuery(BlockchainType.RobinhoodChain, TokenType.Native)) } returns nativeToken
            every {
                token(TokenQuery(BlockchainType.RobinhoodChain, TokenType.Eip20(TOKEN_ADDRESS)))
            } returns eip20Token
        }
        val resolver = UnstoppableTokenResolver(UnstoppableProvider.Barter, repository, marketKit)

        assertEquals(
            UnstoppableTokenResolver.ResolvedAsset("ETH", null),
            resolver.resolve(nativeToken),
        )
        assertEquals(
            UnstoppableTokenResolver.ResolvedAsset("USDC", null),
            resolver.resolve(eip20Token),
        )
    }

    private fun token(type: TokenType) = Token(
        coin = Coin("robinhood-$type", "Robinhood Token", "RHT"),
        blockchain = Blockchain(BlockchainType.RobinhoodChain, "Robinhood Chain", null),
        type = type,
        decimals = 18,
    )

    private companion object {
        const val ROBINHOOD_CHAIN_ID = "4663"
        const val TOKEN_ADDRESS = "0x1111111111111111111111111111111111111111"
        const val NATIVE_EVM_PLACEHOLDER = "0xeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"
    }
}
