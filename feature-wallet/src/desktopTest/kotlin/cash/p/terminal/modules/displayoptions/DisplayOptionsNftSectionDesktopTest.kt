package cash.p.terminal.modules.displayoptions

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.navigation3.runtime.NavBackStack
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class DisplayOptionsNftSectionDesktopTest {

    private fun show(section: NftDisplaySection?, assertions: ComposeUiTest.() -> Unit) = runDesktopComposeUiTest {
        setContent {
            ComposeAppTheme {
                DisplayOptionsScreen(
                    navigation = HSNavigation(NavBackStack()),
                    uiState = DisplayOptionsUiState(
                        isCoinManagerEnabled = true,
                        isRoundingAmountMainPage = true,
                        nftSection = section,
                    ),
                    onPricePeriodChanged = {},
                    onPercentChangeToggled = {},
                    onPriceChangeToggled = {},
                    onRoundingAmountMainPageToggled = {},
                    onShowNftCollectionsToggle = {},
                )
            }
        }
        assertions()
    }

    @Test
    fun nftSection_withGram_showsDefaultHintWithoutInfo() = show(NftDisplaySection(show = true, hasGram = true)) {
        onNodeWithText("Show NFT collections").assertExists()
        onNodeWithText("On by default", substring = true).assertExists()
        onAllNodesWithContentDescription("NFT collections").assertCountEquals(0)
    }

    @Test
    fun nftSection_noGramOn_showsTipHintAndInfo() = show(NftDisplaySection(show = true, hasGram = false)) {
        onNodeWithText("shows a tip on how to turn collections on", substring = true).assertExists()
        onAllNodesWithContentDescription("NFT collections").assertCountEquals(1)
    }

    @Test
    fun nftSection_noGramOff_showsHiddenHint() = show(NftDisplaySection(show = false, hasGram = false)) {
        onNodeWithText("Turn this on to see them again", substring = true).assertExists()
    }

    @Test
    fun nftSection_absent_drawsNoSwitch() = show(null) {
        onNodeWithText("Show NFT collections").assertDoesNotExist()
    }
}
