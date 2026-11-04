package cash.p.terminal.core.providers

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.managers.TonKitManager
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TonFallbackAddressProviderTest {

    private val account = Account(
        id = "account-id",
        name = "Account",
        type = AccountType.Mnemonic(
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about".split(" "),
            "",
        ),
        origin = AccountOrigin.Created,
        level = 0,
    )

    private val tonKitManager = TonKitManager(
        backgroundManager = mockk(relaxed = true),
        hardwarePublicKeyStorage = mockk(relaxed = true),
        platformContexts = mockk(relaxed = true),
        backgroundKeepAliveManager = mockk(relaxed = true),
        networkErrorTracker = mockk(relaxed = true),
        offlineModeManager = mockk(relaxed = true),
        tonKitDatabaseKeyProvider = mockk(relaxed = true),
    )
    private val dispatcher = UnconfinedTestDispatcher()
    private val provider =
        TonFallbackAddressProvider(tonKitManager, TestDispatcherProvider(dispatcher, TestScope(dispatcher)))

    // Expected addresses come from @ton/ton for this mnemonic's m/44'/607'/0' key (see TonWalletDerivationTest).
    @Test
    fun getAddress_w5Wallet_returnsW5Address() = runTest(dispatcher) {
        assertEquals(W5_ADDRESS, provider.getAddress(wallet(BlockchainType.Ton, TokenType.TonW5Native)))
    }

    @Test
    fun getAddress_w5JettonWallet_returnsW5Address() = runTest(dispatcher) {
        assertEquals(W5_ADDRESS, provider.getAddress(wallet(BlockchainType.Ton, TokenType.TonW5Jetton(JETTON_MASTER))))
    }

    @Test
    fun getAddress_v4r2Wallet_returnsV4R2Address() = runTest(dispatcher) {
        assertEquals(V4R2_ADDRESS, provider.getAddress(wallet(BlockchainType.Ton, TokenType.Native)))
    }

    @Test
    fun getAddress_nonTonWallet_returnsNull() = runTest(dispatcher) {
        assertNull(provider.getAddress(wallet(BlockchainType.Ethereum, TokenType.Native)))
    }

    private fun wallet(blockchainType: BlockchainType, tokenType: TokenType): Wallet {
        val token = Token(
            coin = Coin(uid = "coin", name = "Coin", code = "COIN"),
            blockchain = Blockchain(blockchainType, blockchainType.uid, null),
            type = tokenType,
            decimals = 9,
        )
        return mockk {
            every { this@mockk.account } returns this@TonFallbackAddressProviderTest.account
            every { this@mockk.token } returns token
        }
    }

    private companion object {
        const val V4R2_ADDRESS = "UQAzWZa6nM5mJev91wGc7VCSfBoIsYRqKJpV78N8Add9-RKY"
        const val W5_ADDRESS = "UQBHyu-oZVDHRYQ1-rKlGqpHy5yAqanPBirEQNMNOmfHLtaT"
        const val JETTON_MASTER = "EQCxE6mUtQJKFnGfaROTKOt1lZbDiiX1kCixRv7Nw2Id_sDs"
    }
}
