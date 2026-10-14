package cash.p.terminal.ui_compose.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_info_20
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AppBarDesktopTest {

    @Test
    fun menuItem_withDrawableResourceIcon_rendersIconAndReportsClick() = runComposeUiTest {
        var clicks = 0
        setContent {
            ComposeAppTheme {
                AppBar(
                    title = "Title",
                    menuItems = listOf(
                        MenuItem(
                            title = TranslatableString.PlainString("Info"),
                            icon = Res.drawable.ic_info_20,
                            onClick = { clicks++ },
                        )
                    ),
                )
            }
        }

        onNodeWithText("Title").assertExists()
        onNodeWithContentDescription("Info").performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun menuItem_withoutIcon_rendersUppercasedTitleAndReportsClick() = runComposeUiTest {
        var clicks = 0
        setContent {
            ComposeAppTheme {
                AppBar(
                    menuItems = listOf(
                        MenuItem(
                            title = TranslatableString.PlainString("Done"),
                            onClick = { clicks++ },
                        )
                    ),
                )
            }
        }

        onNodeWithText("DONE").performClick()

        assertEquals(1, clicks)
    }
}
