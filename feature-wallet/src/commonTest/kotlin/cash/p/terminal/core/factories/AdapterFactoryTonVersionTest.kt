package cash.p.terminal.core.factories

import cash.p.terminal.core.ICoinManager
import cash.p.terminal.core.adapters.JettonAdapter
import cash.p.terminal.core.adapters.TonTransactionsAdapter
import cash.p.terminal.core.managers.TonKitManager
import cash.p.terminal.core.managers.TonKitWrapper
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.entities.TonWalletVersion
import cash.p.terminal.wallet.meta
import cash.p.terminal.wallet.transaction.TransactionSource
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.tonkit.core.TonKit
import io.horizontalsystems.tonkit.models.SyncState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class AdapterFactoryTonVersionTest {

    private val account = Account(
        id = "account-id",
        name = "Account",
        type = AccountType.Mnemonic(List(12) { "abandon" }, ""),
        origin = AccountOrigin.Created,
        level = 0,
    )
    private val tonBlockchain = Blockchain(BlockchainType.Ton, "TON", null)
    private val w5NativeQuery = TokenQuery(BlockchainType.Ton, TokenType.TonW5Native)

    private val tonKit = mockk<TonKit>(relaxed = true) {
        every { jettonSyncStateFlow } returns MutableStateFlow(SyncState.Synced)
    }
    private val tonKitManager = mockk<TonKitManager> {
        coEvery { getTonKitWrapper(account, TonWalletVersion.W5, BlockchainType.Ton) } returns
            TonKitWrapper(tonKit, mockk(relaxed = true), TonWalletVersion.W5)
    }
    private val coinManager = mockk<ICoinManager> {
        every { getToken(w5NativeQuery) } returns token(TokenType.TonW5Native, decimals = 9)
    }

    private val factory = AdapterFactory(
        btcBlockchainManager = mockk(relaxed = true),
        evmBlockchainManager = mockk(relaxed = true),
        evmSyncSourceManager = mockk(relaxed = true),
        solanaKitManager = mockk(relaxed = true),
        tronKitManager = mockk(relaxed = true),
        tonKitManager = tonKitManager,
        stellarKitManager = mockk(relaxed = true),
        thorchainKitManagers = mockk(relaxed = true),
        moneroKitManager = mockk(relaxed = true),
        backgroundManager = mockk(relaxed = true),
        restoreSettingsManager = mockk(relaxed = true),
        coinManager = coinManager,
        evmLabelManager = mockk(relaxed = true),
        appearanceSettingsStore = mockk(relaxed = true),
        masterNodesRepository = mockk(relaxed = true),
        getBnbAddressUseCase = mockk(relaxed = true),
        feeRateProvider = mockk(relaxed = true),
        dispatcherProvider = mockk(relaxed = true),
        walletManager = mockk(relaxed = true),
        platformChainSupport = mockk { every { isSupported(any<Wallet>()) } returns true },
        zcashHardwareSigners = mockk(relaxed = true),
        stackingManager = mockk(relaxed = true),
    )

    @Test
    fun getAdapterOrNull_w5JettonWallet_usesW5KitAndW5NativeToken() = runTest {
        val wallet = mockk<Wallet>(relaxed = true) {
            every { account } returns this@AdapterFactoryTonVersionTest.account
            every { token } returns token(TokenType.TonW5Jetton(JETTON_MASTER), decimals = 6)
            every { decimal } returns 6
        }

        val adapter = factory.getAdapterOrNull(wallet)

        assertTrue(adapter is JettonAdapter)
        coVerify(exactly = 1) { tonKitManager.getTonKitWrapper(account, TonWalletVersion.W5, BlockchainType.Ton) }
        verify(exactly = 1) { coinManager.getToken(w5NativeQuery) }
    }

    @Test
    fun tonTransactionsAdapter_w5Source_usesW5KitAndW5BaseToken() = runTest {
        val source = TransactionSource(tonBlockchain, account, TokenType.TonW5Native.meta)

        val adapter = factory.tonTransactionsAdapter(source)

        assertTrue(adapter is TonTransactionsAdapter)
        coVerify(exactly = 1) { tonKitManager.getTonKitWrapper(account, TonWalletVersion.W5, BlockchainType.Ton) }
        verify(exactly = 1) { coinManager.getToken(w5NativeQuery) }
    }

    private fun token(type: TokenType, decimals: Int) = Token(
        coin = Coin(uid = "the-open-network", name = "Toncoin", code = "TON"),
        blockchain = tonBlockchain,
        type = type,
        decimals = decimals,
    )

    private companion object {
        const val JETTON_MASTER = "EQCxE6mUtQJKFnGfaROTKOt1lZbDiiX1kCixRv7Nw2Id_sDs"
    }
}
