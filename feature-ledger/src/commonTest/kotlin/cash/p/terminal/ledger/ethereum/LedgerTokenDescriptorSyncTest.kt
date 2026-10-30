package cash.p.terminal.ledger.ethereum

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.ledger.account.LedgerSolanaClearSigning
import cash.p.terminal.manager.IConnectivityManager
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

class LedgerTokenDescriptorSyncTest {

    private val store = LedgerTokenDescriptorStore(InMemoryPreferences())
    private val calClient = mockk<LedgerCalClient> {
        coEvery { erc20Descriptor(any(), any()) } returns DESCRIPTOR
    }
    private val solanaClearSigning = mockk<LedgerSolanaClearSigning>(relaxed = true)
    private val wallets = MutableStateFlow<List<Wallet>>(emptyList())
    private val walletManager = mockk<IWalletManager> { every { activeWalletsFlow } returns wallets }
    private val online = MutableStateFlow(true)
    private val connectivity = mockk<IConnectivityManager> { every { isConnected } returns online }

    @Test
    fun start_ledgerBep20Wallet_cachesItsDescriptor() = runTest {
        startSync()

        wallets.value = listOf(ledgerWallet(BlockchainType.BinanceSmartChain))
        advanceUntilIdle()

        assertContentEquals(DESCRIPTOR, store.descriptor(56, CONTRACT))
    }

    @Test
    fun start_ledgerSplWallet_prefetchesClearSigningDataForItsMint() = runTest {
        startSync()

        wallets.value = listOf(ledgerWallet(BlockchainType.Solana, TokenType.Spl(MINT)))
        advanceUntilIdle()

        coVerify { solanaClearSigning.prefetch("NanoX", MINT) }
    }

    @Test
    fun start_softwareWallet_fetchesNothing() = runTest {
        startSync()

        wallets.value = listOf(wallet(AccountType.EvmAddress(CONTRACT), BlockchainType.Ethereum))
        advanceUntilIdle()

        coVerify(exactly = 0) { calClient.erc20Descriptor(any(), any()) }
        assertNull(store.descriptor(1, CONTRACT))
    }

    @Test
    fun start_descriptorAlreadyCached_fetchesNothing() = runTest {
        store.save(1, CONTRACT, DESCRIPTOR)
        startSync()

        wallets.value = listOf(ledgerWallet(BlockchainType.Ethereum))
        advanceUntilIdle()

        coVerify(exactly = 0) { calClient.erc20Descriptor(any(), any()) }
    }

    @Test
    fun start_offlineAtLaunch_fetchesWhenConnectionReturns() = runTest {
        online.value = false
        wallets.value = listOf(ledgerWallet(BlockchainType.Ethereum))
        startSync()
        advanceUntilIdle()
        coVerify(exactly = 0) { calClient.erc20Descriptor(any(), any()) }

        online.value = true
        advanceUntilIdle()

        assertContentEquals(DESCRIPTOR, store.descriptor(1, CONTRACT))
    }

    @Test
    fun start_requestFailed_retriesAfterReconnect() = runTest {
        coEvery { calClient.erc20Descriptor(any(), any()) } throws IOException("timeout") andThen DESCRIPTOR
        wallets.value = listOf(ledgerWallet(BlockchainType.Ethereum))
        startSync()
        advanceUntilIdle()

        online.value = false
        online.value = true
        advanceUntilIdle()

        assertContentEquals(DESCRIPTOR, store.descriptor(1, CONTRACT))
    }

    @Test
    fun start_storeReadFails_keepsSyncingOtherWallets() = runTest {
        val failingStore = mockk<LedgerTokenDescriptorStore>(relaxUnitFun = true) {
            coEvery { descriptor(1, CONTRACT) } throws IOException("corrupt store")
            coEvery { descriptor(56, CONTRACT) } returns null
        }
        startSync(failingStore)

        wallets.value = listOf(ledgerWallet(BlockchainType.Ethereum), ledgerWallet(BlockchainType.BinanceSmartChain))
        advanceUntilIdle()

        coVerify { failingStore.save(56, CONTRACT, DESCRIPTOR) }
    }

    private fun TestScope.startSync(descriptorStore: LedgerTokenDescriptorStore = store) {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        LedgerTokenDescriptorSync(
            walletManager,
            connectivity,
            descriptorStore,
            calClient,
            solanaClearSigning,
            TestDispatcherProvider(dispatcher, backgroundScope),
        ).start()
    }

    private fun ledgerWallet(blockchainType: BlockchainType, tokenType: TokenType = TokenType.Eip20(CONTRACT)) =
        wallet(AccountType.LedgerDevice("device", "NanoX", "key"), blockchainType, tokenType)

    private fun wallet(
        accountType: AccountType,
        blockchainType: BlockchainType,
        tokenType: TokenType = TokenType.Eip20(CONTRACT),
    ) = mockk<Wallet> {
        every { account.type } returns accountType
        every { token.type } returns tokenType
        every { token.blockchainType } returns blockchainType
    }

    private companion object {
        const val CONTRACT = "0x55d398326f99059ff775485246999027b3197955"
        const val MINT = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"
        val DESCRIPTOR = byteArrayOf(4, 1, 2, 3)
    }
}
