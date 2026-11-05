package cash.p.terminal.modules.createaccount

import cash.p.terminal.core.IAccountFactory
import cash.p.terminal.core.WalletOnboardingStorage
import cash.p.terminal.core.managers.RestoreSettingsManager
import cash.p.terminal.core.managers.WalletActivator
import cash.p.terminal.core.managers.WordsManager
import cash.p.terminal.core.providers.PredefinedBlockchainSettingsProvider
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.PassphraseValidator
import cash.p.terminal.wallet.data.MnemonicKind
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.slip39.Slip39AccountTypeFactory
import cash.p.terminal.wallet.slip39.Slip39SecretSize
import io.horizontalsystems.core.Translator
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.hdwalletkit.Language
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CreateAdvancedAccountViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val accountFactory = mockk<IAccountFactory>()
    private val wordsManager = mockk<WordsManager>()
    private val accountManager = mockk<IAccountManager>(relaxed = true)
    private val walletActivator = mockk<WalletActivator>()
    private val passphraseValidator = PassphraseValidator()
    private val predefinedBlockchainSettingsProvider =
        mockk<PredefinedBlockchainSettingsProvider>(relaxed = true)
    private val restoreSettingsManager = mockk<RestoreSettingsManager>(relaxed = true)
    private val localStorage = mockk<WalletOnboardingStorage>()
    private val slip39AccountTypeFactory = mockk<Slip39AccountTypeFactory>()
    private val slip39Type = AccountType.Slip39(List(20) { "academic" }, PASSPHRASE, ByteArray(16))

    @Before
    fun setUp() {
        mockkObject(Translator)
        every { Translator.getString(any(), *anyVararg()) } returns "translated"
        Dispatchers.setMain(dispatcher)
        every { accountFactory.getNextAccountName() } returns "Wallet 1"
        every { accountFactory.account(any(), any(), any(), any(), any()) } answers {
            account(secondArg())
        }
        every { localStorage.passphraseTermsAgreed } returns false
        coEvery { walletActivator.activateWalletsSuspended(any(), any()) } just Runs
    }

    @After
    fun tearDown() {
        unmockkObject(Translator)
        Dispatchers.resetMain()
    }

    @Test
    fun createMnemonicAccount_spanishLanguage_generatesSeedWithSelectedLanguage() =
        runTest(dispatcher) {
            val words = listOf(
                "fresa",
                "miope",
                "triste",
                "bozal",
                "etica",
                "risa",
                "virgo",
                "nariz",
                "grafico",
                "regla",
                "selva",
                "uva"
            )
            every { wordsManager.generateWords(12, Language.Spanish) } returns words
            val viewModel = createViewModel()

            viewModel.setMnemonicLanguage(Language.Spanish)
            viewModel.createMnemonicAccount()
            advanceUntilIdle()

            val mnemonic = assertIs<AccountType.Mnemonic>(viewModel.success)
            assertEquals(words, mnemonic.words)
            verify(exactly = 1) { wordsManager.generateWords(12, Language.Spanish) }
        }

    @Test
    fun shouldConfirmNonEnglishMnemonic_englishLanguage_returnsFalse() {
        val viewModel = createViewModel()

        assertFalse(viewModel.shouldConfirmNonEnglishMnemonic())
    }

    @Test
    fun setKind_moneroKindDisplaysEnglish_preservesSelectedLanguage() {
        val viewModel = createViewModel()

        viewModel.setMnemonicLanguage(Language.Japanese)
        assertTrue(viewModel.shouldConfirmNonEnglishMnemonic())

        viewModel.setKind(MONERO)

        assertEquals(Language.Japanese, viewModel.selectedLanguage)
        assertEquals(Language.English, viewModel.displayedLanguage)
        assertFalse(viewModel.languageSelectionEnabled)
        assertFalse(viewModel.shouldConfirmNonEnglishMnemonic())
    }

    @Test
    fun setKind_moneroThenBip39_restoresSelectedLanguage() {
        val viewModel = createViewModel()

        viewModel.setMnemonicLanguage(Language.Spanish)
        viewModel.setKind(MONERO)
        viewModel.setKind(WalletKind.Mnemonic(MnemonicKind.Mnemonic12))

        assertEquals(Language.Spanish, viewModel.selectedLanguage)
        assertEquals(Language.Spanish, viewModel.displayedLanguage)
        assertTrue(viewModel.shouldConfirmNonEnglishMnemonic())
    }

    @Test
    fun setMnemonicLanguage_moneroKind_ignoresLanguageChange() {
        val viewModel = createViewModel()

        viewModel.setKind(MONERO)
        viewModel.setMnemonicLanguage(Language.Spanish)

        assertEquals(Language.English, viewModel.selectedLanguage)
        assertEquals(Language.English, viewModel.displayedLanguage)
    }

    @Test
    fun createMnemonicAccount_bip39_preparesZcashSettingsBeforeActivatingWallets() =
        runTest(dispatcher) {
            every { wordsManager.generateWords(12, Language.English) } returns List(12) { "word" }
            val viewModel = createViewModel()

            viewModel.createMnemonicAccount()
            advanceUntilIdle()

            coVerifyOrder {
                predefinedBlockchainSettingsProvider.prepareNew(any(), BlockchainType.Zcash)
                walletActivator.activateWalletsSuspended(any(), any())
            }
        }

    @Test
    fun createMnemonicAccount_bip39_activatesAllDefaultTokens() = runTest(dispatcher) {
        every { wordsManager.generateWords(12, Language.English) } returns List(12) { "word" }
        val viewModel = createViewModel()

        viewModel.createMnemonicAccount()
        advanceUntilIdle()

        assertEquals(TokenQuery.defaultTokenQueries, activatedQueries())
    }

    @Test
    fun createMnemonicAccount_shamir20_savesSlip39FromFactory() = runTest(dispatcher) {
        val viewModel = createShamirViewModel()
        viewModel.enterPassphrase(PASSPHRASE)

        viewModel.createMnemonicAccount()
        advanceUntilIdle()

        coVerify(exactly = 1) { slip39AccountTypeFactory.createNew(Slip39SecretSize.Bits128, PASSPHRASE) }
        verify(exactly = 1) {
            accountFactory.account(any(), slip39Type, AccountOrigin.Created, backedUp = false, fileBackedUp = false)
        }
        verify(exactly = 1) { accountManager.save(any()) }
        assertSame(slip39Type, viewModel.success)
    }

    @Test
    fun createMnemonicAccount_shamir_activatesDefaultTokensExceptZcash() = runTest(dispatcher) {
        val viewModel = createShamirViewModel()

        viewModel.createMnemonicAccount()
        advanceUntilIdle()

        assertEquals(TokenQuery.defaultTokenQueries - TokenQuery.ZcashUnified, activatedQueries())
    }

    @Test
    fun createMnemonicAccount_shamir_preparesMoneroButNotZcash() = runTest(dispatcher) {
        val viewModel = createShamirViewModel()

        viewModel.createMnemonicAccount()
        advanceUntilIdle()

        verify(exactly = 1) { predefinedBlockchainSettingsProvider.prepareNew(any(), BlockchainType.Monero) }
        verify(exactly = 0) { predefinedBlockchainSettingsProvider.prepareNew(any(), BlockchainType.Zcash) }
    }

    @Test
    fun setKind_shamir_forcesEnglishAndKeepsPassphraseBlock() {
        val viewModel = createViewModel()

        viewModel.setMnemonicLanguage(Language.Spanish)
        viewModel.setKind(SHAMIR_20)

        assertTrue(viewModel.isShamir)
        assertFalse(viewModel.languageSelectionEnabled)
        assertEquals(Language.English, viewModel.displayedLanguage)
        assertFalse(viewModel.shouldConfirmNonEnglishMnemonic())
        assertTrue(viewModel.showPassphraseBlock)
    }

    @Test
    fun createMnemonicAccount_shamirFactoryThrows_reportsError() = runTest(dispatcher) {
        coEvery { slip39AccountTypeFactory.createNew(any(), any()) } throws IllegalStateException()
        val viewModel = createViewModel()
        viewModel.setKind(SHAMIR_20)

        viewModel.createMnemonicAccount()
        advanceUntilIdle()

        verify(exactly = 0) { accountManager.save(any()) }
        assertNull(viewModel.success)
        assertEquals("translated", viewModel.error)
        assertFalse(viewModel.loading)
    }

    @Test
    fun onChangePassphrase_shamirDotlessI_setsError() {
        val viewModel = createViewModel()
        viewModel.setKind(SHAMIR_20)
        viewModel.setPassphraseEnabledState(true)

        viewModel.onChangePassphrase(DOTLESS_I)

        assertNotNull(viewModel.passphraseState)
    }

    @Test
    fun onChangePassphrase_bip39DotlessI_accepted() {
        val viewModel = createViewModel()
        viewModel.setPassphraseEnabledState(true)

        viewModel.onChangePassphrase(DOTLESS_I)

        assertNull(viewModel.passphraseState)
    }

    @Test
    fun createMnemonicAccount_bip39PassphraseThenShamir_nonAsciiRejectedNoAccount() = runTest(dispatcher) {
        val viewModel = createShamirViewModel()
        viewModel.setKind(WalletKind.Mnemonic(MnemonicKind.Mnemonic12))
        viewModel.enterPassphrase(DOTLESS_I)
        viewModel.setKind(SHAMIR_20)

        viewModel.createMnemonicAccount()
        advanceUntilIdle()

        assertNotNull(viewModel.passphraseState)
        coVerify(exactly = 0) { slip39AccountTypeFactory.createNew(any(), any()) }
        verify(exactly = 0) { accountManager.save(any()) }
        assertNull(viewModel.success)
    }

    @Test
    fun createMnemonicAccount_shamirRejectedThenBackToBip39_createsAccount() = runTest(dispatcher) {
        every { wordsManager.generateWords(12, Language.English) } returns List(12) { "word" }
        val viewModel = createShamirViewModel()
        viewModel.setKind(WalletKind.Mnemonic(MnemonicKind.Mnemonic12))
        viewModel.enterPassphrase(DOTLESS_I)
        viewModel.setKind(SHAMIR_20)
        viewModel.createMnemonicAccount()
        advanceUntilIdle()

        viewModel.setKind(WalletKind.Mnemonic(MnemonicKind.Mnemonic12))
        viewModel.createMnemonicAccount()
        advanceUntilIdle()

        assertNull(viewModel.passphraseState)
        verify(exactly = 1) { accountManager.save(any()) }
        assertNotNull(viewModel.success)
    }

    @Test
    fun createMnemonicAccount_validThenForbiddenTyped_noAccountWithStalePassphrase() = runTest(dispatcher) {
        val viewModel = createShamirViewModel()
        viewModel.enterPassphrase(PASSPHRASE)
        viewModel.onChangePassphrase(PASSPHRASE + DOTLESS_I)

        viewModel.createMnemonicAccount()
        advanceUntilIdle()

        assertNotNull(viewModel.passphraseState)
        coVerify(exactly = 0) { slip39AccountTypeFactory.createNew(any(), any()) }
    }

    @Test
    fun createMnemonicAccount_passphraseThenMoneroThenBack_createsWithoutHiddenPassphrase() = runTest(dispatcher) {
        every { wordsManager.generateWords(12, Language.English) } returns List(12) { "word" }
        val viewModel = createViewModel()
        viewModel.enterPassphrase(PASSPHRASE)
        viewModel.setKind(MONERO)
        viewModel.setKind(WalletKind.Mnemonic(MnemonicKind.Mnemonic12))

        viewModel.createMnemonicAccount()
        advanceUntilIdle()

        assertFalse(viewModel.passphraseEnabled)
        assertEquals("", assertIs<AccountType.Mnemonic>(viewModel.success).passphrase)
    }

    private fun createShamirViewModel(): CreateAdvancedAccountViewModel {
        coEvery { slip39AccountTypeFactory.createNew(Slip39SecretSize.Bits128, any()) } returns slip39Type
        return createViewModel().apply { setKind(SHAMIR_20) }
    }

    private fun CreateAdvancedAccountViewModel.enterPassphrase(passphrase: String) {
        setPassphraseEnabledState(true)
        onChangePassphrase(passphrase)
        onChangePassphraseConfirmation(passphrase)
    }

    private fun activatedQueries(): List<TokenQuery> {
        val queries = slot<List<TokenQuery>>()
        coVerify(exactly = 1) { walletActivator.activateWalletsSuspended(any(), capture(queries)) }
        return queries.captured
    }

    private fun createViewModel() = CreateAdvancedAccountViewModel(
        accountFactory = accountFactory,
        wordsManager = wordsManager,
        accountManager = accountManager,
        walletActivator = walletActivator,
        passphraseValidator = passphraseValidator,
        predefinedBlockchainSettingsProvider = predefinedBlockchainSettingsProvider,
        restoreSettingsManager = restoreSettingsManager,
        localStorage = localStorage,
        slip39AccountTypeFactory = slip39AccountTypeFactory,
    )

    private fun account(type: AccountType): Account {
        return mockk {
            every { this@mockk.type } returns type
        }
    }

    private companion object {
        const val PASSPHRASE = "TREZOR"
        const val DOTLESS_I = "\u0131"
        val MONERO = WalletKind.Mnemonic(MnemonicKind.Mnemonic25)
        val SHAMIR_20 = WalletKind.Shamir(Slip39SecretSize.Bits128)
    }
}
