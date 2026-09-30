package cash.p.terminal.core.factories

import cash.p.terminal.core.adapters.SplAdapter
import cash.p.terminal.core.managers.EvmBlockchainManager
import cash.p.terminal.core.managers.EvmKitManager
import cash.p.terminal.core.managers.MoneroKitManager
import cash.p.terminal.core.managers.SolanaKitManager
import cash.p.terminal.core.managers.SolanaKitWrapper
import cash.p.terminal.core.managers.StellarKitManager
import cash.p.terminal.core.managers.TonKitManager
import cash.p.terminal.core.managers.TronKitManager
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.transaction.TransactionSource
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.solanakit.SolanaKit
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class AdapterFactoryUnlinkTest {

    private val account = Account(
        id = "account-id",
        name = "Account",
        type = AccountType.EvmAddress("0x"),
        origin = AccountOrigin.Created,
        level = 0,
    )

    private val solanaAccount = account.copy(type = AccountType.SolanaAddress(SOLANA_ADDRESS))

    private val evmBlockchainManager = mockk<EvmBlockchainManager>()
    private val solanaKitManager = mockk<SolanaKitManager>(relaxed = true)
    private val tronKitManager = mockk<TronKitManager>(relaxed = true)
    private val tonKitManager = mockk<TonKitManager>(relaxed = true)
    private val moneroKitManager = mockk<MoneroKitManager>(relaxed = true)
    private val stellarKitManager = mockk<StellarKitManager>(relaxed = true)

    private val factory = AdapterFactory(
        context = mockk(relaxed = true),
        btcBlockchainManager = mockk(relaxed = true),
        evmBlockchainManager = evmBlockchainManager,
        evmSyncSourceManager = mockk(relaxed = true),
        solanaKitManager = solanaKitManager,
        tronKitManager = tronKitManager,
        tonKitManager = tonKitManager,
        stellarKitManager = stellarKitManager,
        moneroKitManager = moneroKitManager,
        backgroundManager = mockk(relaxed = true),
        restoreSettingsManager = mockk(relaxed = true),
        coinManager = mockk(relaxed = true),
        evmLabelManager = mockk(relaxed = true),
        localStorage = mockk(relaxed = true),
        masterNodesRepository = mockk(relaxed = true),
        getBnbAddressUseCase = mockk(relaxed = true),
        feeRateProvider = mockk(relaxed = true),
        dispatcherProvider = mockk(relaxed = true),
        walletManager = mockk(relaxed = true),
    )

    @Test
    fun unlinkAdapter_everySupportedEvmChain_unlinksItsEvmKit() = runTest {
        EvmBlockchainManager.blockchainTypes.forEach { blockchainType ->
            val evmKitManager = mockk<EvmKitManager>(relaxed = true)
            every { evmBlockchainManager.getEvmKitManager(blockchainType) } returns evmKitManager

            factory.unlinkAdapter(transactionSource(blockchainType))

            coVerify(exactly = 1) { evmKitManager.unlink(account) }
        }
    }

    @Test
    fun unlinkAdapter_walletOverload_unlinksSameKitAsTransactionSource() = runTest {
        val evmKitManager = mockk<EvmKitManager>(relaxed = true)
        every {
            evmBlockchainManager.getEvmKitManager(BlockchainType.Avalanche)
        } returns evmKitManager
        val wallet = mockk<Wallet> {
            every { transactionSource } returns transactionSource(BlockchainType.Avalanche)
        }

        factory.unlinkAdapter(wallet)

        coVerify(exactly = 1) { evmKitManager.unlink(account) }
    }

    @Test
    fun unlinkAdapter_nonEvmChain_unlinksItsOwnKitManager() = runTest {
        factory.unlinkAdapter(transactionSource(BlockchainType.Solana))
        coVerify(exactly = 1) { solanaKitManager.unlink(account) }

        factory.unlinkAdapter(transactionSource(BlockchainType.Tron))
        coVerify(exactly = 1) { tronKitManager.unlink(account) }

        factory.unlinkAdapter(transactionSource(BlockchainType.Ton))
        coVerify(exactly = 1) { tonKitManager.unlink(account) }

        factory.unlinkAdapter(transactionSource(BlockchainType.Monero))
        coVerify(exactly = 1) { moneroKitManager.unlink(account) }

        factory.unlinkAdapter(transactionSource(BlockchainType.Stellar))
        coVerify(exactly = 1) { stellarKitManager.unlink(account) }
    }

    @Test
    fun getAdapterOrNull_splWallet_registersTokenAccountBeforeBuildingAdapter() = runTest {
        val solanaKit = mockk<SolanaKit>(relaxed = true)
        // A mocked wrapper turns the adapter's own reads of it into verifiable calls.
        val wrapper = mockk<SolanaKitWrapper> {
            every { this@mockk.solanaKit } returns solanaKit
            every { signer } returns null
        }
        coEvery { solanaKitManager.getSolanaKitWrapper(any()) } returns wrapper

        val adapter = factory.getAdapterOrNull(splWallet())

        assertTrue(adapter is SplAdapter)
        coVerifyOrder {
            solanaKit.addTokenAccount(MINT_ADDRESS, SPL_DECIMALS)
            wrapper.signer
        }
    }

    @Test
    fun getAdapterOrNull_splRegistrationCancelled_releasesKitReference() = runTest {
        val solanaKit = mockk<SolanaKit> {
            coEvery { addTokenAccount(any(), any()) } throws CancellationException()
        }
        val wrapper = mockk<SolanaKitWrapper> { every { this@mockk.solanaKit } returns solanaKit }
        coEvery { solanaKitManager.getSolanaKitWrapper(any()) } returns wrapper

        factory.getAdapterOrNull(splWallet())

        coVerify(exactly = 1) { solanaKitManager.unlink(solanaAccount) }
    }

    @Test
    fun unlinkAdapter_chainWithoutKitManager_doesNothing() = runTest {
        factory.unlinkAdapter(transactionSource(BlockchainType.Bitcoin))

        verify(exactly = 0) { evmBlockchainManager.getEvmKitManager(any()) }
        coVerify(exactly = 0) { solanaKitManager.unlink(any()) }
    }

    private fun transactionSource(blockchainType: BlockchainType) = TransactionSource(
        blockchain = Blockchain(blockchainType, blockchainType.uid, null),
        account = account,
        meta = null,
    )

    private fun splWallet(): Wallet {
        val splToken = Token(
            coin = Coin("Tether", "USDT", "tether"),
            blockchain = Blockchain(BlockchainType.Solana, "Solana", null),
            type = TokenType.Spl(MINT_ADDRESS),
            decimals = SPL_DECIMALS,
        )
        return mockk {
            every { token } returns splToken
            every { this@mockk.account } returns solanaAccount
            every { decimal } returns SPL_DECIMALS
            every { coin } returns splToken.coin
        }
    }

    private companion object {
        const val MINT_ADDRESS = "Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB"
        const val SOLANA_ADDRESS = "Fg6PaFpoGXkYsidMpWTK6W2BeZ7FEfcYkg476zPFsLnS"
        const val SPL_DECIMALS = 6
    }
}
