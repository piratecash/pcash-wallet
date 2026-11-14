package cash.p.terminal.modules.nft

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.nft.ui.previewNftItems
import cash.p.terminal.ui_compose.components.LocalUrlOpener
import cash.p.terminal.ui_compose.components.UrlOpener
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class NftCollectionScreenDesktopTest {

    private fun showScreen(hidden: Boolean) = runDesktopComposeUiTest {
        setContent {
            ComposeAppTheme {
                NftCollectionScreen(
                    uiState = NftCollectionsUiState(
                        items = previewNftItems(1),
                        isLoading = false,
                        hidden = hidden,
                        hiddenCollectionsCount = 3,
                        hasNfts = true,
                    ),
                    onEyeClick = {},
                    onItemClick = {},
                    onManage = {},
                    onBack = {},
                    onReceive = null,
                )
            }
        }
        onNodeWithText("Hidden collections: ${if (hidden) "***" else "3"}").assertExists()
        onNodeWithText("Hidden collections: ${if (hidden) "3" else "***"}").assertDoesNotExist()
    }

    @Test
    fun hiddenCollectionsRow_balanceHidden_masksCount() = showScreen(hidden = true)

    @Test
    fun hiddenCollectionsRow_balanceShown_showsCount() = showScreen(hidden = false)

    private fun showEmptyScreen(
        onReceive: (() -> Unit)?,
        modifier: Modifier = Modifier,
        fontScale: Float = 1f,
        assertions: ComposeUiTest.() -> Unit,
    ) = runDesktopComposeUiTest {
        setContent {
            ComposeAppTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalUrlOpener provides object : UrlOpener {
                        override fun open(url: String) = Unit
                    },
                    LocalDensity provides Density(density.density, fontScale),
                ) {
                    Box(modifier) {
                        NftCollectionScreen(
                            uiState = NftCollectionsUiState(isLoading = false),
                            onEyeClick = {},
                            onItemClick = {},
                            onManage = {},
                            onBack = {},
                            onReceive = onReceive,
                        )
                    }
                }
            }
        }
        assertions()
    }

    @Test
    fun emptyState_receiveAvailable_showsBothButtons() = showEmptyScreen(onReceive = {}) {
        onNodeWithText("Open market").assertExists()
        onNodeWithText("Receive").assertExists()
    }

    @Test
    fun emptyState_noReceiveWallet_hidesReceiveButton() = showEmptyScreen(onReceive = null) {
        onNodeWithText("Open market").assertExists()
        onNodeWithText("Receive").assertDoesNotExist()
    }

    @Test
    fun emptyState_largeTextOnNarrowScreen_keepsReceiveButtonUsable() =
        showEmptyScreen(onReceive = {}, modifier = Modifier.width(320.dp), fontScale = 2f) {
            onNodeWithText("Receive").assertWidthIsAtLeast(80.dp)
        }

    @Test
    fun emptyState_largeTextOnShortScreen_scrollsToButtons() =
        showEmptyScreen(onReceive = {}, modifier = Modifier.size(320.dp, 480.dp), fontScale = 2f) {
            onNodeWithText("Receive").performScrollTo().assertIsDisplayed()
        }
}
