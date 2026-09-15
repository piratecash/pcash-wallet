package cash.p.terminal.modules.mnemonic

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import cash.p.terminal.modules.restoreaccount.MnemonicImportDraft
import cash.p.terminal.ui_compose.components.CheckboxWithInfo
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.MnemonicDerivation
import io.horizontalsystems.hdwalletkit.Language
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "en")
class JapaneseLegacyCellTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun checkbox_labelAndCheckboxClicks_toggleSelectionOnce() {
        val state = mutableStateOf(MnemonicImportDraft.manual("あいこくしん あおぞら"))
        compose.setContent {
            ComposeAppTheme { JapaneseLegacyCell(state.value, { state.value = state.value.selectLegacy(it) }) }
        }
        compose.onNodeWithText("Use legacy P.CASH format").assertIsOff().performClick().assertIsOn()
        compose.onNode(isToggleable()).performTouchInput {
            click(Offset(1f, visibleSize.height / 2f))
        }
        compose.onNode(isToggleable()).assertIsOff()
    }

    @Test
    fun checkbox_infoAndDismiss_doNotChangeSelection() {
        val state = mutableStateOf(MnemonicImportDraft.manual("あいこくしん あおぞら").selectLegacy(true))
        compose.setContent {
            ComposeAppTheme { JapaneseLegacyCell(state.value, { state.value = state.value.selectLegacy(it) }) }
        }
        compose.onNodeWithContentDescription("Info").performClick()
        compose.runOnIdle { assertEquals(MnemonicDerivation.Legacy, state.value.derivation) }
        compose.onNode(hasAnyAncestor(isDialog()) and
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).performClick()
        compose.onNode(isToggleable()).assertIsOn()
    }

    @Test
    fun checkbox_selectedAdapter_reselectsWithoutDeselecting() {
        var selectionCount = 0
        compose.setContent {
            ComposeAppTheme {
                CheckboxWithInfo(
                    title = "P.CASH",
                    checked = true,
                    onCheckedChange = { selectionCount++ },
                    onInfoClick = {},
                )
            }
        }
        compose.onNode(isToggleable()).assertIsOn().performClick().assertIsOn()
        compose.runOnIdle { assertEquals(1, selectionCount) }
    }

    @Test
    fun checkbox_nonJapaneseHintAndNativeMonero_isHidden() {
        val state = mutableStateOf(MnemonicImportDraft.manual("abandon about").copy(language = Language.Japanese))
        compose.setContent {
            ComposeAppTheme { JapaneseLegacyCell(state.value, { state.value = state.value.selectLegacy(it) }) }
        }
        compose.onNode(isToggleable()).assertDoesNotExist()
        compose.runOnIdle { state.value = MnemonicImportDraft.manual("あいこくしん あおぞら").moneroMode(true) }
        compose.onNode(isToggleable()).assertDoesNotExist()
    }
}
