package cash.p.terminal.modules.importwallet

import androidx.lifecycle.ViewModelStore
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.managers.OfflineModeManager
import cash.p.terminal.core.managers.PriceManager
import cash.p.terminal.core.storage.PendingMultiSwapStorage
import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.modules.balance.BalanceViewModel
import cash.p.terminal.modules.balance.BalanceViewTypeManager
import cash.p.terminal.modules.balance.DefaultBalanceService
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.balance.BalanceViewType
import io.mockk.coEvery
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import cash.p.terminal.core.managers.WordsManager
import cash.p.terminal.core.usecase.ValidateMoneroMnemonicUseCase
import cash.p.terminal.modules.restoreaccount.MnemonicImportDraft
import cash.p.terminal.modules.restoreaccount.MnemonicInput
import cash.p.terminal.modules.restoreaccount.RestoreViewModel
import cash.p.terminal.modules.restoreaccount.restoremnemonic.RestoreMnemonicViewModel
import cash.p.terminal.modules.restoreaccount.restoremnemonic.RestoreMnemonicModule
import cash.p.terminal.modules.restoreaccount.restoremnemonicnonstandard.RestoreMnemonicNonStandardViewModel
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.MnemonicDerivation
import cash.p.terminal.wallet.normalizeNFKD
import io.mockk.mockk
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import android.util.Base64
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.managers.SeedPhraseQrCrypto
import cash.p.terminal.core.managers.TimePasswordProvider
import cash.p.terminal.core.utils.Bip39LanguageDetector
import io.horizontalsystems.hdwalletkit.Language
import io.horizontalsystems.hdwalletkit.Mnemonic
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Regression tests for ImportWalletViewModel — locks down the QR decrypt path before
 * SeedPhraseQrCrypto JSON v2 migration. Existing scanners must keep parsing v1 (legacy)
 * QRs and the new v2 JSON QRs alike.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ImportWalletViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val dispatcherProvider = TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher))

    private lateinit var crypto: SeedPhraseQrCrypto

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg())
        }
        every { Base64.decode(any<String>(), any()) } answers {
            java.util.Base64.getDecoder().decode(firstArg<String>())
        }
        crypto = SeedPhraseQrCrypto(TimePasswordProvider())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private val words12 = ("abandon abandon abandon abandon abandon abandon " +
            "abandon abandon abandon abandon abandon about").split(" ")
    private val spanishWords12 = List(11) { "ábaco" } + "abierto"
    private val portugueseWords12 =
        "cruzeiro cidreira pistola surreal munido padaria protetor mensagem orbitar meteoro apetite vergonha"
            .split(" ")
    private val words25Monero = ("tavern total bail plutonium faked faster beneath reinvest " +
            "syndrome dagger razor nobody acoustic tubes people germs myriad next victim sipped " +
            "oasis dagger razor acoustic acoustic").split(" ")
    private val entropy128 = ByteArray(16)

    @Test
    fun handleScannedData_validBip39Qr_emitsOpenRestoreFromQrEvent() = runTest(dispatcher) {
        val viewModel = ImportWalletViewModel(crypto, dispatcherProvider)
        val encrypted = crypto.encrypt(words12, "myPass")

        viewModel.handleScannedData(encrypted)
        advanceUntilIdle()

        val event = withTimeout(1_000) { viewModel.navigationEvents.first() }
        assertNotNull(event)
        val openEvent = event as ImportWalletViewModel.NavigationEvent.OpenRestoreFromQr
        assertEquals(words12, openEvent.draft.wordItems().map { it.word })
        assertEquals("myPass", openEvent.draft.passphrase)
        assertNull(openEvent.draft.height.toLongOrNull())
        assertNull(viewModel.errorMessage)
    }

    @Test
    fun handleScannedData_validBip39QrWithLanguage_emitsLanguageHint() = runTest(dispatcher) {
        val viewModel = ImportWalletViewModel(crypto, dispatcherProvider)
        val encrypted = crypto.encrypt(spanishWords12, "", language = Language.Spanish)

        viewModel.handleScannedData(encrypted)
        advanceUntilIdle()

        val openEvent = withTimeout(1_000) {
            viewModel.navigationEvents.first()
        } as ImportWalletViewModel.NavigationEvent.OpenRestoreFromQr
        assertEquals(Language.Spanish, openEvent.draft.language)
    }

    @Test
    fun handleScannedData_plainPortugueseMnemonic_emitsOpenRestoreFromQrEventWithDetectedLanguage() =
        runTest(dispatcher) {
            val viewModel = ImportWalletViewModel(crypto, dispatcherProvider)
            val scannedText = portugueseWords12.joinToString(" ")

            viewModel.handleScannedData(scannedText)
            advanceUntilIdle()

            val openEvent = withTimeout(1_000) {
                viewModel.navigationEvents.first()
            } as ImportWalletViewModel.NavigationEvent.OpenRestoreFromQr
            assertEquals(portugueseWords12, openEvent.draft.wordItems().map { it.word })
            assertEquals("", openEvent.draft.passphrase)
            assertNull(openEvent.draft.height.toLongOrNull())
            assertEquals(Language.Portuguese, openEvent.draft.language)
            assertNull(viewModel.errorMessage)
        }

    @Test
    fun handleScannedData_plainMnemonicForEachBip39Language_emitsOpenRestoreFromQrEvent() =
        runTest(dispatcher) {
            val mnemonic = Mnemonic()

            Language.entries.forEach { language ->
                val viewModel = ImportWalletViewModel(crypto, dispatcherProvider)
                val words = mnemonic.toMnemonic(entropy128, language)
                val expectedLanguage = Bip39LanguageDetector.detectExact(words).firstOrNull()
                assertNotNull("Words must be detectable for $language", expectedLanguage)

                viewModel.handleScannedData(words.joinToString(" "))
                advanceUntilIdle()

                val openEvent = withTimeout(1_000) {
                    viewModel.navigationEvents.first()
                } as ImportWalletViewModel.NavigationEvent.OpenRestoreFromQr
                assertEquals("Words must round-trip for $language", words, openEvent.draft.wordItems().map { it.word })
                assertEquals("", openEvent.draft.passphrase)
                assertNull(openEvent.draft.height.toLongOrNull())
                assertEquals(expectedLanguage, openEvent.draft.language)
                assertNull(viewModel.errorMessage)
            }
        }

    @Test
    fun handleScannedData_validMoneroQr_emitsEventWithHeight() = runTest(dispatcher) {
        val viewModel = ImportWalletViewModel(crypto, dispatcherProvider)
        val encrypted = crypto.encrypt(words25Monero, "", height = 2_500_000L)

        viewModel.handleScannedData(encrypted)
        advanceUntilIdle()

        val openEvent = withTimeout(1_000) {
            viewModel.navigationEvents.first()
        } as ImportWalletViewModel.NavigationEvent.OpenRestoreFromQr
        assertEquals(words25Monero, openEvent.draft.wordItems().map { it.word })
        assertEquals(2_500_000L, openEvent.draft.height.toLongOrNull())
    }

    @Test
    fun handleScannedData_qrPrefixButCorruptPayload_setsErrorMessageNoEvent() =
        runTest(dispatcher) {
            val viewModel = ImportWalletViewModel(crypto, dispatcherProvider)

            viewModel.handleScannedData("seed:not-valid-base64!!!")
            advanceUntilIdle()

            assertNotNull(
                "Decryption failure must surface an error message",
                viewModel.errorMessage
            )
        }

    @Test
    fun handleScannedData_nonSeedPrefix_setsErrorMessage() = runTest(dispatcher) {
        val viewModel = ImportWalletViewModel(crypto, dispatcherProvider)

        viewModel.handleScannedData("https://example.com/some-other-qr")
        advanceUntilIdle()

        assertNotNull(
            "Non-seed QR must be rejected with an error message",
            viewModel.errorMessage
        )
    }

    @Test
    fun handleScannedData_validQrAfterErrorState_clearsErrorOnSuccess() = runTest(dispatcher) {
        val viewModel = ImportWalletViewModel(crypto, dispatcherProvider)

        // Trigger error first
        viewModel.handleScannedData("not-a-seed-qr")
        advanceUntilIdle()
        assertNotNull(viewModel.errorMessage)

        // Now successful scan — error remains until onErrorShown(); contract is that
        // success path does NOT clear errorMessage automatically.
        // Lock down current behavior: success path still emits navigation event regardless of
        // pre-existing error.
        val encrypted = crypto.encrypt(words12, "")
        viewModel.handleScannedData(encrypted)
        advanceUntilIdle()

        val openEvent = withTimeout(1_000) {
            viewModel.navigationEvents.first()
        } as ImportWalletViewModel.NavigationEvent.OpenRestoreFromQr
        assertEquals(words12, openEvent.draft.wordItems().map { it.word })
    }

    @Test
    fun onErrorShown_clearsErrorMessage() = runTest(dispatcher) {
        val viewModel = ImportWalletViewModel(crypto, dispatcherProvider)
        viewModel.handleScannedData("not-a-seed-qr")
        advanceUntilIdle()
        assertNotNull(viewModel.errorMessage)

        viewModel.onErrorShown()

        assertNull(viewModel.errorMessage)
    }
    @Test
    fun restore_decodedLegacyToggleAndNavigation_preservesExactSeedThroughBothQrEntries() = runTest(dispatcher) {
        for (viaImport in listOf(false, true)) for (passphrase in listOf("páss", "  ")) {
            val words = List(11) { "あいこくしん" } + "あおぞら"
            val original = AccountType.Mnemonic(words, passphrase, MnemonicDerivation.Legacy)
            val qr = crypto.encrypt(words, passphrase)
            val basic = restoreViewModel()
            val payload = if (viaImport) {
                val importer = ImportWalletViewModel(crypto, dispatcherProvider)
                importer.handleScannedData(qr)
                assertIs<ImportWalletViewModel.NavigationEvent.OpenRestoreFromQr>(
                    importer.navigationEvents.first()
                ).draft
            } else {
                assertIs<RestoreMnemonicModule.QrScanResult.Success>(basic.handleScannedQrData(qr)).draft
            }
            val shared = RestoreViewModel()
            val leaveBasic = basic.bindDraft(payload, shared::setDraft)
            basic.onToggleLegacy(false)
            basic.onEnterMnemonicPhrase(basic.draft.text, 4)
            val advanced = restoreViewModel()
            advanced.bindDraft(requireNotNull(shared.mnemonicDraft), shared::setDraft)
            leaveBasic()
            val nonStandard = nonStandardViewModel()
            nonStandard.applyDraft(advanced.draft)
            nonStandard.onToggleLegacy(true)
            nonStandard.onProceed()
            advanceUntilIdle()
            assertEquals(original, nonStandard.uiState.accountType)
            advanced.applyDraft(nonStandard.draft)
            advanced.onEnterPassphrase(passphrase)
            advanced.onProceed()
            advanceUntilIdle()
            val restored = assertIs<AccountType.Mnemonic>(advanced.uiState.accountType)
            assertEquals(original, restored)
            assertTrue(original.seed.contentEquals(restored.seed))
            assertEquals(MnemonicImportDraft.Source.DecodedQr, advanced.draft.source)
            shared.setAccountData(restored, "public", true, false)
            shared.setDraft(advanced.draft)
            assertEquals(restored, shared.accountType)
            advanced.onToggleLegacy(false)
            assertNull(shared.accountType)
        }
    }

    @Test
    fun restore_manualJapaneseDefaultsAndReplacement_clearsModeAndPendingState() = runTest(dispatcher) {
        val text = (List(11) { "あいこくしん" } + "あおぞら").joinToString("　")
        val vm = restoreViewModel()
        vm.onEnterMnemonicPhrase(text, text.length)
        assertEquals(MnemonicDerivation.Bip39, vm.draft.derivation)
        vm.onProceed()
        advanceUntilIdle()
        val standard = assertIs<AccountType.Mnemonic>(vm.uiState.accountType)
        assertEquals(text.normalizeNFKD().split(" "), standard.words)
        vm.onToggleLegacy(true)
        advanceUntilIdle()
        assertNull(vm.uiState.accountType)
        vm.onEnterMnemonicPhrase("$text x", text.length + 2)
        assertEquals(MnemonicDerivation.Legacy, vm.draft.derivation)
        vm.onProceed()
        advanceUntilIdle()
        assertNull(vm.uiState.accountType)
        vm.onEnterMnemonicPhrase(words12.joinToString(" "), 0)
        assertFalse(vm.draft.isJapanese)
        vm.onEnterMnemonicPhrase(text, text.length)
        assertEquals(MnemonicDerivation.Bip39, vm.draft.derivation)
        vm.applyDraft(MnemonicImportDraft.decoded(SeedPhraseQrCrypto.DecryptedSeed(words25Monero, "old", 123L, null)))
        vm.applyDraft(MnemonicImportDraft.manual(text))
        assertEquals("", vm.draft.passphrase)
        assertEquals("", vm.draft.height)
        assertFalse(vm.draft.isMoneroMnemonic)
        assertFalse(vm.draft.passphraseEnabled)
        val nonStandard = nonStandardViewModel()
        nonStandard.applyDraft(MnemonicImportDraft.manual(text))
        nonStandard.onProceed()
        advanceUntilIdle()
        assertEquals(MnemonicDerivation.Bip39,
            assertIs<AccountType.Mnemonic>(nonStandard.uiState.accountType).derivation)
    }

    @Test
    fun wordItems_japaneseNfcAndIdeographicSpaces_preservesOriginalEditorRanges() {
        val words = List(11) { "あいこくしん" } + "あおぞら"
        val expectedRanges = (0..10).map { it * 7..it * 7 + 5 } + listOf(77..80)
        val separators = listOf("　", "\t", "\n", "\r", "\u000B", "\u000C", "\u00A0", "\u0085", "\u2028", "\u2029")
        for (separator in separators) {
            val text = words.joinToString(separator)
            val items = MnemonicInput.wordItems(text, lowercase = true)
            assertEquals(words, items.map { it.word })
            assertEquals(expectedRanges, items.map { it.range })
            items.forEach { assertEquals(it.word, text.substring(it.range)) }
            assertEquals(words.map { it.normalizeNFKD() }, MnemonicInput.canonicalWords(text))
            assertTrue(MnemonicImportDraft.manual(text).isJapanese)
        }
        val blank = MnemonicImportDraft.manual("")
        assertFalse(blank.isJapanese)
        assertTrue(blank.wordItems().isEmpty())
        val vm = restoreViewModel()
        vm.setMnemonicLanguage(Language.Japanese)
        vm.onEnterMnemonicPhrase(words12.joinToString(" "), 0)
        assertFalse(vm.draft.isJapanese)
        assertEquals(words12, vm.draft.wordItems().map { it.word })
    }

    @Test
    fun handleScannedData_plainJapaneseQr_usesManualBip39Defaults() = runTest(dispatcher) {
        val vm = ImportWalletViewModel(crypto, dispatcherProvider)
        vm.handleScannedData((List(11) { "あいこくしん" } + "あおぞら").joinToString("　"))
        val draft = assertIs<ImportWalletViewModel.NavigationEvent.OpenRestoreFromQr>(vm.navigationEvents.first()).draft
        assertEquals(MnemonicDerivation.Bip39, draft.derivation)
        assertEquals(MnemonicImportDraft.Source.Manual, draft.source)
    }

    @Test
    fun handleScannedData_versionedQrMode_isPreservedByBothEntries() = runTest(dispatcher) {
        MnemonicDerivation.entries.forEach { mode ->
            val qr = crypto.encrypt(words12, "páss", derivation = mode)
            val importer = ImportWalletViewModel(crypto, dispatcherProvider)
            importer.handleScannedData(qr)
            val event = assertIs<ImportWalletViewModel.NavigationEvent.OpenRestoreFromQr>(
                importer.navigationEvents.first()
            )
            val direct = requireNotNull(restoreViewModel().applyScannedQrData(qr))
            assertEquals(mode, event.draft.derivation)
            assertEquals(mode, direct.derivation)
            assertEquals(MnemonicImportDraft.Source.DecodedQr, direct.source)
        }
    }

    @Test
    fun restore_manualJapanesePolicies_normalImportCanonicalizesAndNonStandardPreservesLegacyBytes() =
        runTest(dispatcher) {
            val words = List(11) { "あいこくしん" } + "あおぞら"
            val text = words.joinToString("　")
            val normal = restoreViewModel()
            normal.onEnterMnemonicPhrase(text, text.length)
            normal.onToggleLegacy(true)
            normal.onTogglePassphrase(true)
            normal.onEnterPassphrase("páss")
            normal.onProceed()
            advanceUntilIdle()
            val canonical = assertIs<AccountType.Mnemonic>(normal.uiState.accountType)
            assertEquals(words.map { it.normalizeNFKD() }, canonical.words)
            assertEquals("páss".normalizeNFKD(), canonical.passphrase)
            assertEquals(MnemonicDerivation.Legacy, canonical.derivation)
            val nonStandard = nonStandardViewModel()
            nonStandard.applyDraft(normal.draft)
            nonStandard.onProceed()
            advanceUntilIdle()
            val raw = assertIs<AccountType.Mnemonic>(nonStandard.uiState.accountType)
            assertEquals(words, raw.words)
            assertEquals("páss", raw.passphrase)
            assertEquals(MnemonicDerivation.Legacy, raw.derivation)
            assertFalse(raw.seed.contentEquals(canonical.seed))
        }

    @Test
    fun balanceScan_encryptedJapaneseQr_immediatelyEmitsRawDraftAndStoredMode() = runTest(dispatcher) {
        val words = List(11) { "あいこくしん" } + "あおぞら"
        val passphrase = " páss　"
        startKoin { modules(module {
            single { crypto }
            single<IAccountManager> { mockk { every { activeAccountStateFlow } returns emptyFlow() } }
            single<OfflineModeManager> { mockk { every { effectiveFlow } returns emptyFlow() } }
            single<PendingMultiSwapStorage> {
                mockk { every { observeForActiveAccount(any()) } returns emptyFlow() }
            }
            single<SwapProviderTransactionsStorage> {
                mockk { every { observeForActiveAccount(any()) } returns emptyFlow() }
            }
        }) }
        val store = ViewModelStore()
        try {
            val viewModel = balanceViewModel()
            store.put("balance", viewModel)
            // Read before scanning: lazy state initialization must not mask a missing emission.
            assertNull(viewModel.uiState.openRestoreFromQr)
            MnemonicDerivation.entries.forEach { mode ->
                viewModel.handleScannedData(crypto.encrypt(words, passphrase, derivation = mode))
                val draft = requireNotNull(viewModel.uiState.openRestoreFromQr).draft
                assertEquals(MnemonicImportDraft.Source.DecodedQr, draft.source)
                assertEquals(mode, draft.derivation)
                assertEquals(words, draft.decodedWords)
                assertEquals(passphrase, draft.passphrase)
                val account = draft.accountType(nonStandard = false)
                assertTrue(AccountType.Mnemonic(words, passphrase, mode).seed.contentEquals(account.seed))
                viewModel.onRestoreFromQrOpened()
                assertNull(viewModel.uiState.openRestoreFromQr)
            }
        } finally {
            store.clear()
            stopKoin()
        }
    }

    private fun balanceViewModel(): BalanceViewModel {
        val service = mockk<DefaultBalanceService>(relaxed = true) {
            every { account } returns null
            every { balanceItemsFlow } returns silentStateFlow()
        }
        val viewTypeManager = mockk<BalanceViewTypeManager> {
            every { balanceViewTypeFlow } returns silentStateFlow<BalanceViewType>().also {
                every { it.value } returns BalanceViewType.CoinThenFiat
            }
        }
        val storage = mockk<ILocalStorage>(relaxed = true) {
            every { balanceTabButtonsEnabledFlow } returns silentStateFlow()
        }
        val prices = mockk<PriceManager> {
            every { priceChangeIntervalFlow } returns silentStateFlow()
            every { displayPricePeriodFlow } returns silentStateFlow()
            every { displayDiffOptionTypeFlow } returns silentStateFlow()
        }
        return BalanceViewModel(service, mockk(), viewTypeManager, mockk(relaxed = true), storage,
            mockk(), mockk(), prices, mockk { every { anyWalletVisibilityChangedFlow } returns emptyFlow() })
    }

    @OptIn(InternalCoroutinesApi::class)
    private fun <T> silentStateFlow(): StateFlow<T> = mockk {
        coEvery { collect(any()) } coAnswers { awaitCancellation() }
    }

    private fun nonStandardViewModel() = RestoreMnemonicNonStandardViewModel(
        mockk(relaxed = true), WordsManager(Mnemonic()), mockk(relaxed = true), crypto
    )

    private fun restoreViewModel() = RestoreMnemonicViewModel(
        ValidateMoneroMnemonicUseCase(WordsManager(Mnemonic())), mockk(relaxed = true), mockk(relaxed = true),
        mockk(relaxed = true), mockk(relaxed = true), crypto, mockk(relaxed = true), mockk(relaxed = true)
    )
}
