package cash.p.terminal.modules.multiswap

import cash.p.terminal.modules.multiswap.providers.IMultiSwapProvider
import cash.p.terminal.modules.multiswap.providers.OffChainSwapProvider
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionData
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionSettings
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.WalletFactory
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.policy.HardwareWalletTokenPolicy
import io.horizontalsystems.core.Translator
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class SwapConfirmViewModelCardHintTest : SwapConfirmViewModelTestBase() {

    private val cardWallet: Wallet = checkNotNull(
        WalletFactory(
            object : HardwareWalletTokenPolicy {
                override fun isSupported(blockchainType: BlockchainType, tokenType: TokenType) = true
            }
        ).create(
            token = token,
            account = Account(
                id = "card-account",
                name = "Card",
                type = AccountType.HardwareCard(
                    cardId = "card-id",
                    backupCardsCount = 0,
                    walletPublicKey = "wallet-public-key",
                    signedHashes = 0,
                ),
                origin = AccountOrigin.Created,
                level = 0,
            ),
            hardwarePublicKey = null,
        )
    )

    private val depositQuote: ISwapFinalQuote = SwapFinalQuoteEvm(
        tokenIn = token,
        tokenOut = token,
        amountIn = BigDecimal.ONE,
        amountOut = BigDecimal.ONE,
        amountOutMin = null,
        sendTransactionData = SendTransactionData.Tron.Regular(address = "deposit-address", amount = BigDecimal.ONE),
        priceImpact = null,
        fields = emptyList(),
    )

    private fun orderProvider(
        signsOrder: Boolean = true,
        finalQuote: suspend () -> ISwapFinalQuote = { depositQuote },
    ): IMultiSwapProvider = mockk<OffChainSwapProvider>(relaxed = true) {
        every { mevProtectionAvailable } returns false
        every { signsOrderWithAccountKey } returns signsOrder
        coEvery { fetchFinalQuote(any(), any(), any(), any(), any(), any()) } coAnswers { finalQuote() }
    }

    @Test
    fun createState_cardAccountOrderPlaced_showsTapCardAgainHint() = runTest(dispatcher) {
        val viewModel = createViewModel(orderProvider(), wallet = cardWallet)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.tapCardAgainHintVisible)
    }

    @Test
    fun createState_cardAccountOrderPending_hidesHint() = runTest(dispatcher) {
        val pendingQuote = CompletableDeferred<ISwapFinalQuote>()
        val viewModel = createViewModel(orderProvider { pendingQuote.await() }, wallet = cardWallet)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.loading)
        assertFalse(viewModel.uiState.tapCardAgainHintVisible)
        pendingQuote.complete(depositQuote)
    }

    @Test
    fun createState_cardAccountOrderFailed_hidesHint() = runTest(dispatcher) {
        mockkObject(Translator)
        every { Translator.getString(any(), *anyVararg()) } returns "error"
        val viewModel = createViewModel(orderProvider { error("order failed") }, wallet = cardWallet)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.tapCardAgainHintVisible)
    }

    @Test
    fun createState_softwareAccount_hidesHint() = runTest(dispatcher) {
        val viewModel = createViewModel(orderProvider())
        advanceUntilIdle()

        assertFalse(viewModel.uiState.tapCardAgainHintVisible)
    }

    @Test
    fun sendTransactionSettingsChanged_orderSigningProvider_keepsPendingOrderSignature() = runTest(dispatcher) {
        val pendingQuote = CompletableDeferred<ISwapFinalQuote>()
        val provider = orderProvider { pendingQuote.await() }
        val viewModel = createViewModel(provider, wallet = cardWallet)
        advanceUntilIdle()

        sendTransactionSettings.value = SendTransactionSettings.Ton("receive-address")
        pendingQuote.complete(depositQuote)
        advanceUntilIdle()

        coVerify(exactly = 1) { provider.fetchFinalQuote(any(), any(), any(), any(), any(), any()) }
        assertTrue(viewModel.uiState.tapCardAgainHintVisible)
    }

    @Test
    fun sendTransactionSettingsChanged_providerWithoutOrderSignature_refetchesFinalQuote() = runTest(dispatcher) {
        val provider = orderProvider(signsOrder = false)
        createViewModel(provider, wallet = cardWallet)
        advanceUntilIdle()

        sendTransactionSettings.value = SendTransactionSettings.Ton("receive-address")
        advanceUntilIdle()

        coVerify(exactly = 2) { provider.fetchFinalQuote(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun createState_providerWithoutOrderSignature_hidesHint() = runTest(dispatcher) {
        val viewModel = createViewModel(orderProvider(signsOrder = false), wallet = cardWallet)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.tapCardAgainHintVisible)
    }
}
