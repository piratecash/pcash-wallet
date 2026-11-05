package cash.p.terminal.modules.restoreaccount.restoreshamir

import cash.p.terminal.core.IAccountFactory
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.managers.SeedPhraseQrCrypto
import cash.p.terminal.core.managers.TimePasswordProvider
import cash.p.terminal.modules.restoreaccount.restoreshamir.Slip39TestShares.ADVANCED_20
import cash.p.terminal.modules.restoreaccount.restoreshamir.Slip39TestShares.BASIC_20_3OF6
import cash.p.terminal.modules.restoreaccount.restoreshamir.Slip39TestShares.BASIC_EXT_20_FIRST
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Restore_Error_EmptyPassphrase
import cash.p.terminal.resources.shamir_error_duplicate
import cash.p.terminal.resources.shamir_error_invalid_share
import cash.p.terminal.resources.shamir_error_invalid_words
import cash.p.terminal.resources.shamir_error_mixed_sets
import cash.p.terminal.resources.shamir_error_other_set
import cash.p.terminal.resources.shamir_error_passphrase_chars
import cash.p.terminal.resources.shamir_error_word_count
import cash.p.terminal.ui.compose.components.ThirdPartyKeyboardWarningState
import cash.p.terminal.wallet.slip39.EncryptedMasterSecret
import cash.p.terminal.wallet.slip39.Slip39AccountTypeFactory
import cash.p.terminal.wallet.slip39.Slip39Shamir
import io.horizontalsystems.core.IThirdKeyboard
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.security.SecureRandom
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RestoreShamirViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val accountFactory = mockk<IAccountFactory> { every { getNextAccountName() } returns "Wallet 1" }
    private val qrCrypto = SeedPhraseQrCrypto(TimePasswordProvider())
    private val thirdKeyboardStorage = object : IThirdKeyboard {
        override var isThirdPartyKeyboardAllowed = false
    }
    private val viewModel by lazy { createViewModel() }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun onSubmitShare_firstOfThreeRequired_acceptsAndShowsSingleGroupProgress() {
        submit(BASIC_20_3OF6[0])

        val state = viewModel.uiState
        assertEquals(1, state.acceptedCount)
        assertEquals(ShamirProgress.SingleGroup(required = 3, accepted = 1), state.progress)
        assertEquals("", state.input)
        assertNull(state.inputError)
        assertFalse(state.isComplete)
    }

    @Test
    fun onSubmitShare_unknownWord_highlightsWordAndRejects() {
        submit(BASIC_20_3OF6[0].replaceFirst("extra", "zzzz"))

        assertEquals(Res.string.shamir_error_invalid_words, viewModel.uiState.inputError)
        assertEquals(listOf(0..3), viewModel.uiState.invalidWordRanges)
        assertEquals(0, viewModel.uiState.acceptedCount)
    }

    @Test
    fun onSubmitShare_nineteenWords_rejectsWordCount() {
        submit(BASIC_20_3OF6[0].substringBeforeLast(' '))

        assertEquals(Res.string.shamir_error_word_count, viewModel.uiState.inputError)
        assertEquals(0, viewModel.uiState.acceptedCount)
    }

    @Test
    fun onSubmitShare_brokenChecksum_rejectsInvalidShare() {
        submit(BASIC_20_3OF6[0].replace("modern", "academic"))

        assertEquals(Res.string.shamir_error_invalid_share, viewModel.uiState.inputError)
        assertEquals(0, viewModel.uiState.acceptedCount)
    }

    @Test
    fun onSubmitShare_sameShareTwice_rejectsDuplicateAndKeepsInput() {
        submit(BASIC_20_3OF6[0])
        submit(BASIC_20_3OF6[0])

        assertEquals(Res.string.shamir_error_duplicate, viewModel.uiState.inputError)
        assertEquals(BASIC_20_3OF6[0], viewModel.uiState.input)
        assertEquals(1, viewModel.uiState.acceptedCount)
    }

    @Test
    fun onSubmitShare_shareOfAnotherSet_rejectsOtherSet() {
        submit(BASIC_20_3OF6[0])
        submit(BASIC_EXT_20_FIRST)

        assertEquals(Res.string.shamir_error_other_set, viewModel.uiState.inputError)
        assertEquals(1, viewModel.uiState.acceptedCount)
    }

    @Test
    fun onSubmitShare_groupFromTwoSplitsOfOneSecret_clearsGroupWithMixedSetsError() {
        val ems = EncryptedMasterSecret(
            identifier = 7,
            extendable = false,
            iterationExponent = 0,
            ciphertext = ByteArray(16) { it.toByte() },
        )
        val first = Slip39Shamir.split(ems, 2, 3, SecureRandom())
        val second = Slip39Shamir.split(ems, 2, 3, SecureRandom())

        submit(first[0].joinToString(" "))
        submit(second[1].joinToString(" "))

        val state = viewModel.uiState
        assertEquals(Res.string.shamir_error_mixed_sets, state.inputError)
        assertEquals(0, state.acceptedCount)
        assertNull(state.progress)
        assertFalse(state.isComplete)
    }

    @Test
    fun onSubmitShare_twoGroupSet_showsGroupProgressThenCompletes() {
        val groupThreshold = Slip39Shamir.parseShare(ADVANCED_20[0]).groupThreshold

        submit(ADVANCED_20[0])
        val progress = assertIs<ShamirProgress.MultiGroup>(viewModel.uiState.progress)
        assertEquals(groupThreshold, progress.groupThreshold)

        ADVANCED_20.drop(1).forEach(::submit)
        assertTrue(viewModel.uiState.isComplete)
        assertNull(viewModel.uiState.inputError)
    }

    @Test
    fun onRestore_completeSetWithPassphrase_buildsCanonicalShareAccount() {
        BASIC_20_3OF6.forEach(::submit)
        viewModel.onTogglePassphrase(true)
        viewModel.onEnterPassphrase("TREZOR")

        viewModel.onRestore()

        val accountType = checkNotNull(viewModel.uiState.accountType)
        assertEquals(canonicalShare(), accountType.words)
        assertEquals("TREZOR", accountType.passphrase)
        assertContentEquals(
            Slip39Shamir.combine(BASIC_20_3OF6.map(Slip39Shamir::parseShare)).decrypt("TREZOR"),
            accountType.seed
        )
    }

    @Test
    fun onEnterPassphrase_nonAsciiCharacter_setsErrorAndBlocksRestore() {
        BASIC_20_3OF6.forEach(::submit)
        viewModel.onTogglePassphrase(true)

        viewModel.onEnterPassphrase("пароль")
        viewModel.onRestore()

        assertEquals(Res.string.shamir_error_passphrase_chars, viewModel.uiState.passphraseError)
        assertNull(viewModel.uiState.accountType)
    }

    @Test
    fun onRestore_enabledEmptyPassphrase_setsEmptyPassphraseError() {
        BASIC_20_3OF6.forEach(::submit)
        viewModel.onTogglePassphrase(true)
        viewModel.onEnterPassphrase("")

        viewModel.onRestore()

        assertEquals(Res.string.Restore_Error_EmptyPassphrase, viewModel.uiState.passphraseError)
        assertNull(viewModel.uiState.accountType)
    }

    @Test
    fun onRestore_enabledSpacesOnlyPassphrase_setsEmptyPassphraseError() {
        BASIC_20_3OF6.forEach(::submit)
        viewModel.onTogglePassphrase(true)
        viewModel.onEnterPassphrase("   ")

        viewModel.onRestore()

        assertEquals(Res.string.Restore_Error_EmptyPassphrase, viewModel.uiState.passphraseError)
        assertNull(viewModel.uiState.accountType)
    }

    @Test
    fun accountName_notEntered_usesNextAccountName() {
        viewModel.onEnterName("  ")

        assertEquals("Wallet 1", viewModel.accountName)
    }

    @Test
    fun applyPrefill_canonicalShareWithPassphrase_completesWithPassphraseEnabled() {
        val canonical = canonicalShare()

        viewModel.applyPrefill(ShamirPrefill(canonical, "TREZOR"))

        val state = viewModel.uiState
        assertTrue(state.isComplete)
        assertTrue(state.passphraseEnabled)
        assertEquals("TREZOR", state.passphrase)
    }

    @Test
    fun onScanned_encryptedSlip39Qr_completes() {
        viewModel.onScanned(qrCrypto.encrypt(canonicalShare(), "", kind = SeedPhraseQrCrypto.KIND_SLIP39))

        assertTrue(viewModel.uiState.isComplete)
    }

    @Test
    fun onScanned_encryptedBip39Qr_rejectsInvalidShare() {
        viewModel.onScanned(qrCrypto.encrypt(Slip39TestShares.BIP39_12.split(" "), ""))

        assertEquals(Res.string.shamir_error_invalid_share, viewModel.uiState.inputError)
        assertEquals(0, viewModel.uiState.acceptedCount)
    }

    @Test
    fun onScanned_rejectedShareWithPassphrase_keepsPassphraseOff() {
        submit(BASIC_20_3OF6[0])
        val foreignSecret = EncryptedMasterSecret(
            identifier = 7,
            extendable = false,
            iterationExponent = 0,
            ciphertext = ByteArray(16) { it.toByte() },
        )
        val foreignShare = Slip39Shamir.split(foreignSecret, threshold = 1, count = 1, SecureRandom()).single()

        viewModel.onScanned(qrCrypto.encrypt(foreignShare, "FOREIGN", kind = SeedPhraseQrCrypto.KIND_SLIP39))
        BASIC_20_3OF6.drop(1).forEach(::submit)

        val state = viewModel.uiState
        assertTrue(state.isComplete)
        assertFalse(state.passphraseEnabled)
        assertEquals("", state.passphrase)
    }

    @Test
    fun onRestore_formEditedWhileDeriving_restoresSubmittedValues() {
        val deriveDispatcher = StandardTestDispatcher(dispatcher.scheduler)
        val viewModel = createViewModel(deriveDispatcher)
        BASIC_20_3OF6.forEach { viewModel.submitShare(it) }
        viewModel.onTogglePassphrase(true)
        viewModel.onEnterPassphrase("TREZOR")
        viewModel.onRestore()

        viewModel.onEnterPassphrase("OTHER")
        viewModel.onTogglePassphrase(false)
        viewModel.onEnterName("Renamed")
        deriveDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState
        assertTrue(state.passphraseEnabled)
        assertEquals("TREZOR", state.passphrase)
        assertEquals("TREZOR", checkNotNull(state.accountType).passphrase)
        assertEquals("Wallet 1", viewModel.accountName)
    }

    @Test
    fun isThirdPartyKeyboardAllowed_storedConsent_reflectsStorage() {
        thirdKeyboardStorage.isThirdPartyKeyboardAllowed = true

        assertTrue(viewModel.isThirdPartyKeyboardAllowed)
    }

    @Test
    fun keyboardWarning_thirdPartyKeyboardAllowed_persistsConsentAndStopsWarning() {
        val warning = ThirdPartyKeyboardWarningState(viewModel, isThirdPartyKeyboard = { true })

        warning.onSecretInput()
        assertTrue(warning.isVisible)

        warning.allow()
        warning.onSecretInput()

        assertTrue(thirdKeyboardStorage.isThirdPartyKeyboardAllowed)
        assertFalse(warning.isVisible)
    }

    @Test
    fun keyboardWarning_systemKeyboard_staysHidden() {
        val warning = ThirdPartyKeyboardWarningState(viewModel, isThirdPartyKeyboard = { false })

        warning.onSecretInput()

        assertFalse(warning.isVisible)
    }

    private fun createViewModel(deriveDispatcher: CoroutineDispatcher = dispatcher) = RestoreShamirViewModel(
        accountFactory,
        Slip39AccountTypeFactory(TestDispatcherProvider(deriveDispatcher, CoroutineScope(dispatcher))),
        qrCrypto,
        thirdKeyboardStorage,
    )

    private fun submit(share: String) = viewModel.submitShare(share)

    private fun RestoreShamirViewModel.submitShare(share: String) {
        onEnterShare(share, share.length)
        onSubmitShare()
    }

    private fun canonicalShare(): List<String> =
        Slip39Shamir.split(Slip39Shamir.combine(BASIC_20_3OF6.map(Slip39Shamir::parseShare)), 1, 1, SecureRandom()).single()
}
