package cash.p.terminal.modules.balance

import cash.p.terminal.core.managers.EvmSyncSourceManager
import cash.p.terminal.core.managers.UserDeletedWalletManager
import cash.p.terminal.core.usecase.OfflineModeUseCase
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.WalletFactory
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.zcashDisableTokenQueryIds
import cash.p.terminal.wallet.zcashMnemonicAccount
import cash.p.terminal.wallet.zcashTransparentWallet
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Observable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

class BalanceActiveWalletRepositoryTest {

    private val walletManager = mockk<IWalletManager>(relaxed = true)
    private val accountManager = mockk<IAccountManager>(relaxed = true)
    private val walletsAccountId = MutableStateFlow<String?>(null)
    private val userDeletedWalletManager = mockk<UserDeletedWalletManager>(relaxed = true)
    private val offlineModeUseCase = mockk<OfflineModeUseCase>(relaxed = true)
    private val evmSyncSourceManager = mockk<EvmSyncSourceManager>()

    private val account = zcashMnemonicAccount()

    private fun createRepository(): BalanceActiveWalletRepository {
        every { walletManager.activeWalletsFlow } returns MutableStateFlow(emptyList())
        every { walletManager.activeWalletsAccountIdFlow } returns walletsAccountId
        every { evmSyncSourceManager.syncSourceObservable } returns Observable.never()
        return BalanceActiveWalletRepository(
            walletManager,
            accountManager,
            userDeletedWalletManager,
            offlineModeUseCase,
            evmSyncSourceManager,
        )
    }

    @Test
    fun itemsObservable_walletsOfAnotherAccount_emitsNothing() {
        every { accountManager.activeAccount } returns account
        walletsAccountId.value = "previous"

        createRepository().itemsObservable.test().assertNoValues()
    }

    @Test
    fun itemsObservable_activeAccountWalletsLoaded_emitsThem() {
        val wallet = bitcoinWallet()
        every { accountManager.activeAccount } returns account
        every { walletManager.activeWallets } returns listOf(wallet)
        val items = createRepository().itemsObservable.test()

        walletsAccountId.value = account.id

        assertEquals(listOf(wallet), items.values().last())
    }

    @Test
    fun itemsObservable_activeAccountHasNoWallets_emitsEmptyList() {
        every { accountManager.activeAccount } returns account
        every { walletManager.activeWallets } returns emptyList()
        walletsAccountId.value = account.id

        val emitted = createRepository().itemsObservable.test().values()

        assertEquals(listOf(emptyList<Wallet>()), emitted.distinct())
    }

    @Test
    fun disable_regularWallet_resetsOfflineModeAfterDeletion() = runTest {
        val wallet = bitcoinWallet()

        createRepository().disable(wallet)

        coVerifyOrder {
            walletManager.deleteByWallet(wallet)
            offlineModeUseCase.resetIfBlockchainRemoved(account, BlockchainType.Bitcoin)
        }
    }

    @Test
    fun disable_zcashWallet_resetsOfflineModeAfterGroupedDeletion() = runTest {
        val wallet = zcashTransparentWallet(account)

        createRepository().disable(wallet)

        coVerifyOrder {
            walletManager.deleteByTokenQueryIds(account.id, zcashDisableTokenQueryIds)
            offlineModeUseCase.resetIfBlockchainRemoved(account, BlockchainType.Zcash)
        }
    }

    private fun bitcoinWallet(): Wallet = checkNotNull(
        WalletFactory(mockk(relaxed = true)).create(
            token = Token(
                coin = Coin(uid = "bitcoin", name = "Bitcoin", code = "BTC"),
                blockchain = Blockchain(type = BlockchainType.Bitcoin, name = "Bitcoin", eip3091url = null),
                type = TokenType.Derived(TokenType.Derivation.Bip84),
                decimals = 8,
            ),
            account = account,
            hardwarePublicKey = null,
        )
    )
}
