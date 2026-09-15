package cash.p.terminal.feature.miniapp.domain.usecase

import cash.p.terminal.feature.miniapp.data.api.EvmNonceResponseDto
import cash.p.terminal.feature.miniapp.data.api.MiniAppApi
import cash.p.terminal.feature.miniapp.data.api.PCashWalletRequestDto
import cash.p.terminal.feature.miniapp.data.api.PCashWalletResponseDto
import cash.p.terminal.feature.miniapp.domain.storage.IUniqueCodeStorage
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import io.horizontalsystems.core.DispatcherProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.math.BigDecimal
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private class TestDispatcherProvider(dispatcher: CoroutineDispatcher) : DispatcherProvider {
    override val io: CoroutineDispatcher = dispatcher
    override val default: CoroutineDispatcher = dispatcher
    override val main: CoroutineDispatcher = dispatcher
    override val applicationScope: CoroutineScope = CoroutineScope(dispatcher)
}

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectMiniAppWalletUseCaseTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val dispatcherProvider = TestDispatcherProvider(dispatcher)

    private val miniAppApi = mockk<MiniAppApi>()
    private val evmPersonalSigner = mockk<EvmPersonalSigner>()
    private val getTonAddressUseCase = mockk<GetTonAddressUseCase>()
    private val getSpecialProposalDataUseCase = mockk<GetSpecialProposalDataUseCase>()
    private val uniqueCodeStorage = mockk<IUniqueCodeStorage>(relaxed = true)

    private val useCase = ConnectMiniAppWalletUseCase(
        miniAppApi,
        evmPersonalSigner,
        getTonAddressUseCase,
        getSpecialProposalDataUseCase,
        uniqueCodeStorage,
        dispatcherProvider
    )

    private val account = Account(
        id = "account-1",
        name = "Wallet",
        type = AccountType.Mnemonic(words = listOf("a", "b"), passphrase = ""),
        origin = AccountOrigin.Created,
        level = 0
    )

    private val jwt = "jwt-token"
    private val endpoint = "https://api.example.com/"
    private val tonAddress = "ton-address"
    private val premiumAddress = "0x1234567890123456789012345678901234567890"
    private val nonce = "abc"
    private val signature = "0xsignature"

    private fun stubHappyPath() {
        coEvery { getTonAddressUseCase.getAddress(account) } returns tonAddress
        coEvery { evmPersonalSigner.address(account) } returns premiumAddress
        coEvery { miniAppApi.getEvmNonce(jwt, endpoint) } returns EvmNonceResponseDto(
            nonce = nonce,
            expiresIn = 600
        )
        coEvery { evmPersonalSigner.signPersonalMessage(account, any()) } returns signature
        coEvery { getSpecialProposalDataUseCase.getPirateCosaBalances(premiumAddress) } returns
            (BigDecimal.ONE to BigDecimal.TEN)
        coEvery { miniAppApi.submitPCashWallet(jwt, endpoint, any()) } returns PCashWalletResponseDto(
            uniqueCode = "returned-code"
        )
    }

    @Test
    fun invoke_happyPath_signsAddressNoncePairAndSubmits() = runTest(dispatcher) {
        stubHappyPath()
        val messageSlot = slot<String>()
        coEvery {
            evmPersonalSigner.signPersonalMessage(account, capture(messageSlot))
        } returns signature
        val requestSlot = slot<PCashWalletRequestDto>()
        coEvery {
            miniAppApi.submitPCashWallet(jwt, endpoint, capture(requestSlot))
        } returns PCashWalletResponseDto(uniqueCode = "returned-code")

        useCase(account, jwt, endpoint)

        assertEquals("Address: $premiumAddress\nNonce: $nonce", messageSlot.captured)
        assertEquals(premiumAddress, requestSlot.captured.premiumAddress)
        assertEquals(signature, requestSlot.captured.premiumSignature)
    }

    @Test
    fun invoke_happyPath_fetchesNonceBeforeSigning() = runTest(dispatcher) {
        stubHappyPath()

        useCase(account, jwt, endpoint)

        coVerifyOrder {
            miniAppApi.getEvmNonce(jwt, endpoint)
            evmPersonalSigner.signPersonalMessage(account, any())
            miniAppApi.submitPCashWallet(jwt, endpoint, any())
        }
    }

    @Test
    fun invoke_signerHasNoAddress_throwsNoEvmSignerException() = runTest(dispatcher) {
        coEvery { getTonAddressUseCase.getAddress(account) } returns tonAddress
        coEvery { evmPersonalSigner.address(account) } returns null

        assertFailsWith<NoEvmSignerException> {
            useCase(account, jwt, endpoint)
        }

        coVerify(exactly = 0) { miniAppApi.submitPCashWallet(any(), any(), any()) }
    }

    @Test
    fun invoke_signerReturnsNullSignature_throwsNoEvmSignerException() = runTest(dispatcher) {
        coEvery { getTonAddressUseCase.getAddress(account) } returns tonAddress
        coEvery { evmPersonalSigner.address(account) } returns premiumAddress
        coEvery { miniAppApi.getEvmNonce(jwt, endpoint) } returns EvmNonceResponseDto(
            nonce = nonce,
            expiresIn = 600
        )
        coEvery { evmPersonalSigner.signPersonalMessage(account, any()) } returns null

        assertFailsWith<NoEvmSignerException> {
            useCase(account, jwt, endpoint)
        }

        coVerify(exactly = 0) { miniAppApi.submitPCashWallet(any(), any(), any()) }
    }

    @Test
    fun invoke_submitSucceeds_storesUniqueCodeAndAccountId() = runTest(dispatcher) {
        stubHappyPath()

        useCase(account, jwt, endpoint)

        coVerify(exactly = 1) { uniqueCodeStorage.connectedAccountId = account.id }
        coVerify(exactly = 1) { uniqueCodeStorage.uniqueCode = "returned-code" }
    }

    @Test
    fun invoke_submitFails_doesNotTouchStorage() = runTest(dispatcher) {
        stubHappyPath()
        coEvery { miniAppApi.submitPCashWallet(jwt, endpoint, any()) } throws RuntimeException("boom")

        assertFailsWith<RuntimeException> {
            useCase(account, jwt, endpoint)
        }

        coVerify(exactly = 0) { uniqueCodeStorage.connectedAccountId = any() }
        coVerify(exactly = 0) { uniqueCodeStorage.uniqueCode = any() }
    }
}
