package cash.p.terminal.modules.restoreaccount

import android.app.Application
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import cash.p.terminal.core.managers.SeedPhraseQrCrypto
import cash.p.terminal.core.managers.TimePasswordProvider
import cash.p.terminal.modules.restoreaccount.restoremnemonic.RestoreMnemonicViewModel
import cash.p.terminal.modules.restoreaccount.restoremnemonicnonstandard.RestoreMnemonicNonStandardViewModel
import cash.p.terminal.strings.helpers.Translator
import io.horizontalsystems.hdwalletkit.Language
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import cash.p.terminal.modules.enablecoin.restoresettings.TokenConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.test.assertIs

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RestoreViewModelTest {
    @get:Rule val compose = createComposeRule()
    private val crypto = SeedPhraseQrCrypto(TimePasswordProvider())
    private val japanese = List(11) { "あいこくしん" } + "あおぞら"

    @Test
    fun scanner_japaneseLegacyWithoutPassphrase_survivesBothDeliveryOrders() =
        assertDecodedRecovery(japanese, "", null)

    @Test
    fun scanner_japaneseLegacyRawPassphrase_navigatesOnceFromActiveEditor() =
        assertDecodedRecovery(japanese, "  が\u3000é  ", null)

    @Test
    fun scanner_nativeMonero_survivesBothDeliveryOrders() = assertDecodedRecovery(
        ("tavern total bail plutonium faked faster beneath reinvest syndrome dagger razor nobody " +
            "acoustic tubes people germs myriad next victim sipped oasis dagger razor acoustic acoustic").split(" "),
        "", 123456L
    )

    @Test
    fun scanner_plainText_survivesBothDeliveryOrders() =
        assertScannerRecovery(japanese.joinToString(" "), MnemonicImportDraft.manual(japanese.joinToString(" "))
            .copy(language = Language.Japanese))

    @Test
    fun scanner_invalidStructuredResult_showsErrorWithoutImporting() {
        mockkObject(Translator)
        try {
            every { Translator.getString(any()) } returns "Invalid QR"
            assertScannerRecovery(crypto.encrypt(listOf("あおぞら"), ""), MnemonicImportDraft(), "Invalid QR")
        } finally {
            unmockkObject(Translator)
        }
    }

    private fun assertDecodedRecovery(words: List<String>, passphrase: String, height: Long?) {
        val text = words.joinToString(" ")
        val expected = MnemonicImportDraft(
            text = text, cursorPosition = text.length, passphrase = passphrase,
            passphraseEnabled = passphrase.isNotEmpty(), height = height?.toString().orEmpty(),
            isMoneroMnemonic = height != null, language = if (height == null) Language.Japanese else Language.English,
            source = MnemonicImportDraft.Source.DecodedQr, decodedWords = words
        )
        assertScannerRecovery(crypto.encrypt(words, passphrase, height), expected)
    }

    private fun assertScannerRecovery(text: String, expected: MnemonicImportDraft, error: String? = null) {
        var fixture by mutableStateOf<ScannerFixture?>(null)
        compose.setContent { fixture?.Content() }
        for (nonStandard in listOf(false, true)) for (beforeRecreation in listOf(false, true)) {
            lateinit var current: ScannerFixture
            compose.runOnIdle { current = ScannerFixture(nonStandard); fixture = current }
            compose.waitForIdle()
            val oldCallback = current.callback
            val oldViewModel = current.viewModel
            compose.runOnIdle { current.visible = false }
            compose.runOnIdle {
                current.lifecycle.currentState = Lifecycle.State.STARTED
                if (beforeRecreation) oldCallback(text)
                current.visible = true
            }
            compose.runOnIdle {
                assertEquals(0, current.applied)
                current.lifecycle.currentState = Lifecycle.State.RESUMED
            }
            compose.runOnIdle {
                if (!beforeRecreation) oldCallback(text)
                assertEquals(MnemonicImportDraft(), oldViewModel.draft)
            }
            assertActiveScanner(current, expected, error)
            assertScannerRebind(current, expected, error)
            compose.runOnIdle { fixture = null }
        }
    }

    private fun assertScannerRebind(current: ScannerFixture, expected: MnemonicImportDraft, error: String?) {
        val rebound = if (error == null) expected.copy(cursorPosition = 6, selectionStart = 2) else expected
        // Focus loss intentionally collapses a platform selection, independently of draft rebinding.
        if (error == null) compose.onNode(hasSetTextAction()).performSemanticsAction(SemanticsActions.SetSelection) {
            it(2, 6, true)
        }
        compose.runOnIdle { current.visible = false }
        compose.runOnIdle { current.visible = true }
        assertActiveScanner(current, rebound, error, checkError = false)
    }

    private fun assertActiveScanner(
        fixture: ScannerFixture, expected: MnemonicImportDraft, error: String?, checkError: Boolean = true,
    ) {
        compose.onNode(hasSetTextAction()).assertTextEquals(expected.text)
        compose.runOnIdle {
            assertEquals("Shared selection start", expected.selectionStart, fixture.owner.mnemonicDraft.selectionStart)
            assertEquals("Shared cursor", expected.cursorPosition, fixture.owner.mnemonicDraft.cursorPosition)
            assertEquals(expected, fixture.owner.mnemonicDraft)
            assertEquals(expected, fixture.viewModel.draft)
            assertEquals(TextRange(expected.selectionStart, expected.cursorPosition), fixture.editor.selection)
            assertEquals(if (error == null) 1 else 0, fixture.applied)
            assertEquals(if (expected.passphraseEnabled && !fixture.nonStandard) 1 else 0, fixture.navigations)
            assertNull(fixture.owner.accountType)
            assertNull(fixture.owner.scannedText.value)
            when (val vm = fixture.viewModel) {
                is RestoreMnemonicViewModel -> {
                    assertEquals(expected.isMoneroMnemonic, vm.uiState.isMoneroMnemonic)
                    assertEquals(expected.height, vm.uiState.height)
                    if (checkError) assertEquals(error, vm.uiState.error)
                    assertNull(vm.uiState.accountType)
                }
                is RestoreMnemonicNonStandardViewModel -> {
                    if (checkError) assertEquals(error, vm.uiState.error)
                    assertNull(vm.uiState.accountType)
                }
            }
        }
    }

    private inner class ScannerFixture(val nonStandard: Boolean) : LifecycleOwner {
        override val lifecycle = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
        val owner = RestoreViewModel()
        var visible by mutableStateOf(true)
        lateinit var callback: (String) -> Unit
        lateinit var viewModel: MnemonicImportViewModel<*>
        lateinit var editor: TextFieldValue
        var applied = 0
        var navigations = 0

        @Composable
        fun Content(modifier: Modifier = Modifier) {
            if (!visible) return
            val vm = remember {
                if (nonStandard) RestoreMnemonicNonStandardViewModel(mockk(relaxed = true), mockk(), mockk(), crypto)
                else RestoreMnemonicViewModel(mockk(), mockk(), mockk(), mockk(), mockk(), crypto,
                    mockk(relaxed = true), mockk())
            }
            var value by remember {
                val draft = owner.mnemonicDraft
                mutableStateOf(TextFieldValue(draft.text, TextRange(draft.selectionStart, draft.cursorPosition)))
            }
            CompositionLocalProvider(LocalLifecycleOwner provides this) {
                callback = rememberMnemonicScanner(vm, owner) { draft ->
                    applied++
                    value = TextFieldValue(draft.text, TextRange(draft.selectionStart, draft.cursorPosition))
                    if (!nonStandard && draft.passphraseEnabled) navigations++
                }
                BasicTextField(value, {
                    value = it
                    vm.onEnterMnemonicPhrase(it.text, it.selection.end, it.selection.start)
                }, modifier)
            }
            viewModel = vm
            editor = value
        }
    }

    @Test
    fun tokenConfigResult_afterSet_publishesEnteredResultUntilHandled() {
        val viewModel = RestoreViewModel()
        val initialConfig = TokenConfig("100", restoreAsNew = false)
        val resultConfig = TokenConfig("200", restoreAsNew = false)

        viewModel.setTokenInitialConfig(initialConfig)
        assertEquals("100", viewModel.tokenInitialConfig?.birthdayHeight)

        viewModel.setTokenConfig(resultConfig)

        assertNull(viewModel.tokenInitialConfig)
        val result = assertIs<TokenConfigResult.Entered>(viewModel.tokenConfigResult.value)
        assertEquals(resultConfig, result.config)

        viewModel.clearTokenConfigResult(result.id)

        assertNull(viewModel.tokenConfigResult.value)
    }

    @Test
    fun tokenConfigResult_afterCancel_publishesCancelledResultUntilHandled() {
        val viewModel = RestoreViewModel()

        viewModel.setTokenInitialConfig(TokenConfig("100", restoreAsNew = false))
        assertEquals("100", viewModel.tokenInitialConfig?.birthdayHeight)

        viewModel.cancelTokenConfig()

        assertNull(viewModel.tokenInitialConfig)
        val result = assertIs<TokenConfigResult.Cancelled>(viewModel.tokenConfigResult.value)

        viewModel.clearTokenConfigResult(result.id)

        assertNull(viewModel.tokenConfigResult.value)
    }

    @Test
    fun tokenConfigResult_sameConfigEnteredTwice_publishesDistinctResults() {
        val viewModel = RestoreViewModel()
        val resultConfig = TokenConfig("200", restoreAsNew = false)

        viewModel.setTokenConfig(resultConfig)
        val first = assertIs<TokenConfigResult.Entered>(viewModel.tokenConfigResult.value)
        viewModel.clearTokenConfigResult(first.id)

        viewModel.setTokenConfig(resultConfig)
        val second = assertIs<TokenConfigResult.Entered>(viewModel.tokenConfigResult.value)

        assertEquals(resultConfig, second.config)
        assertEquals(first.id + 1, second.id)
    }
}
