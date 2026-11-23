package cash.p.terminal.modules.signmessage

import cash.p.terminal.core.adapters.BitcoinAdapter
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.sign_message_failed
import cash.p.terminal.resources.sign_message_not_ready
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.entities.UsedAddress
import io.horizontalsystems.bitcoincore.messages.MessageSignatureFormat
import io.horizontalsystems.core.Translator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SignMessageViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val adapter: BitcoinAdapter = mockk {
        every { receiveAddress } returns RECEIVE_ADDRESS
        every { usedAddresses(false) } returns listOf(UsedAddress(0, USED_ADDRESS, ""))
        every { usedAddresses(true) } returns emptyList()
    }
    private val adapterManager: IAdapterManager = mockk {
        every { getAdapterForWallet<BitcoinAdapter>(any()) } returns adapter
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkObject(Translator)
        every { Translator.getString(Res.string.sign_message_failed) } returns FAILED
        every { Translator.getString(Res.string.sign_message_not_ready) } returns NOT_READY
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun init_nativeSegwitSoftware_defaultsToStandardAndReceiveAddress() {
        val state = viewModel(TokenType.Derivation.Bip84).uiState

        assertEquals(RECEIVE_ADDRESS, state.address)
        assertEquals(listOf(MessageSignatureFormat.Standard, MessageSignatureFormat.Bip322), state.formats)
        assertEquals(MessageSignatureFormat.Standard, state.selectedFormat)
    }

    @Test
    fun init_taprootTrezor_noFormatsAndSignDisabled() {
        val viewModel = viewModel(TokenType.Derivation.Bip86, AccountType.TrezorDevice("id", "T3T1", "2.8.0", "xpub"))
        viewModel.onMessageChange("hello")

        assertEquals(emptyList<MessageSignatureFormat>(), viewModel.uiState.formats)
        assertFalse(viewModel.uiState.signEnabled)
    }

    @Test
    fun sign_selectedAddressAndFormat_passesMessageByteExactAndShowsResult() = runTest(dispatcher) {
        val message = "  Ich bestätige\r\nmeine Adresse \t"
        coEvery { adapter.signMessage(USED_ADDRESS, message, MessageSignatureFormat.Bip322) } returns SIGNATURE
        val viewModel = viewModel(TokenType.Derivation.Bip84)

        viewModel.onAddressSelect(USED_ADDRESS)
        viewModel.onFormatSelect(MessageSignatureFormat.Bip322)
        viewModel.onMessageChange(message)
        viewModel.sign()

        assertEquals(SignedMessage(SIGNATURE, USED_ADDRESS, MessageSignatureFormat.Bip322, message), viewModel.uiState.result)
        assertFalse(viewModel.uiState.signing)
    }

    @Test
    fun sign_adapterThrows_showsErrorAndKeepsForm() = runTest(dispatcher) {
        coEvery { adapter.signMessage(any(), any(), any()) } throws IllegalStateException("card")
        val viewModel = viewModel(TokenType.Derivation.Bip84)
        viewModel.onMessageChange("hello")

        viewModel.sign()

        assertEquals(FAILED, viewModel.uiState.error)
        assertEquals("hello", viewModel.uiState.message)
        assertNull(viewModel.uiState.result)
        assertFalse(viewModel.uiState.signing)
    }

    @Test
    fun sign_adapterNotReady_showsNotReady() {
        every { adapterManager.getAdapterForWallet<BitcoinAdapter>(any()) } returns null
        val viewModel = viewModel(TokenType.Derivation.Bip84)
        viewModel.onMessageChange("hello")

        viewModel.sign()

        assertEquals(NOT_READY, viewModel.uiState.error)
        assertNull(viewModel.addressSelectInput())
    }

    @Test
    fun sign_whileSigning_secondTapIgnored() = runTest(dispatcher) {
        val pending = CompletableDeferred<String>()
        coEvery { adapter.signMessage(any(), any(), any()) } coAnswers { pending.await() }
        val viewModel = viewModel(TokenType.Derivation.Bip84)
        viewModel.onMessageChange("hello")

        viewModel.sign()
        viewModel.sign()
        pending.complete(SIGNATURE)

        coVerify(exactly = 1) { adapter.signMessage(any(), any(), any()) }
    }

    @Test
    fun addressSelectInput_adapterReady_listsReceiveAndChangeWithSelection() {
        val viewModel = viewModel(TokenType.Derivation.Bip84)
        viewModel.onAddressSelect(USED_ADDRESS)

        val input = viewModel.addressSelectInput()

        assertEquals(
            SignAddressSelectInput(RECEIVE_ADDRESS, listOf(UsedAddress(0, USED_ADDRESS, "")), emptyList(), USED_ADDRESS),
            input,
        )
    }

    private fun viewModel(
        derivation: TokenType.Derivation,
        accountType: AccountType = AccountType.Mnemonic(listOf("abandon"), ""),
    ): SignMessageViewModel {
        val account: Account = mockk { every { type } returns accountType }
        val wallet: Wallet = mockk {
            every { token.type } returns TokenType.Derived(derivation)
            every { this@mockk.account } returns account
        }
        return SignMessageViewModel(wallet, adapterManager)
    }

    private companion object {
        const val RECEIVE_ADDRESS = "bc1qreceive"
        const val USED_ADDRESS = "bc1qused"
        const val SIGNATURE = "c2lnbmF0dXJl"
        const val FAILED = "failed"
        const val NOT_READY = "not ready"
    }
}
