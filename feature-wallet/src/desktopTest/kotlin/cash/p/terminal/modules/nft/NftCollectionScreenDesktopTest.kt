package cash.p.terminal.modules.nft

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import cash.p.terminal.modules.nft.ui.previewNftItems
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
}
