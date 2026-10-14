package cash.p.terminal.ui_compose.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class HsSwitchDesktopTest {

    @Test
    fun hsSwitch_default_matchesFormerM2TouchTarget() = runComposeUiTest {
        setContent { ComposeAppTheme { HsSwitch(checked = true, onCheckedChange = {}) } }

        val bounds = onRoot().getBoundsInRoot()
        assertEquals(38.dp, bounds.width)
        assertEquals(24.dp, bounds.height)
        val toggle = onNode(isToggleable()).getBoundsInRoot()
        assertEquals(38.dp, toggle.width)
        assertEquals(24.dp, toggle.height)
    }

    @Test
    fun hsSwitch_click_togglesValue() = runComposeUiTest {
        var checked by mutableStateOf(false)
        setContent { ComposeAppTheme { HsSwitch(checked = checked, onCheckedChange = { checked = it }) } }

        onNode(isToggleable()).performClick()

        assertTrue(checked)
    }
}
