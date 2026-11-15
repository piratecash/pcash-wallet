package cash.p.terminal.modules.nft.gram

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import cash.p.terminal.modules.nft.NftGramHintsUiState
import cash.p.terminal.modules.nft.NftVersionHint
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.entities.TonWalletVersion
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NftGramHintsDesktopTest {

    private val events = mutableListOf<String>()

    private fun show(state: NftGramHintsUiState, gridShown: Boolean = false, assertions: ComposeUiTest.() -> Unit) =
        runDesktopComposeUiTest {
            setContent {
                ComposeAppTheme {
                    LazyColumn {
                        nftGramHints(
                            state = state,
                            gridShown = gridShown,
                            onInfo = { events += "info" },
                            onAdd = { events += "add ${it.title}" },
                            onAddDefault = { events += "addDefault" },
                            onDismiss = { events += "dismiss ${it.title}" },
                        )
                    }
                }
            }
            assertions()
        }

    @Test
    fun addBlock_infoAndAdd_reportTheirActions() = show(NftGramHintsUiState(showAddGramBlock = true)) {
        onNodeWithText("Add the GRAM blockchain to see NFTs").assertExists()
        onNodeWithContentDescription("NFT collections").performClick()
        onNodeWithText("Add").performClick()

        assertEquals(listOf("info", "addDefault"), events)
    }

    @Test
    fun versionRow_gridNotShown_hasOneTitleWithoutInfo() =
        show(NftGramHintsUiState(rows = listOf(NftVersionHint(TonWalletVersion.W5, 12)))) {
            onAllNodesWithText("Collections").assertCountEquals(1)
            onAllNodesWithContentDescription("NFT collections").assertCountEquals(0)
            onNodeWithText("The GRAM W5 wallet has 12 NFTs").assertExists()
        }

    @Test
    fun versionRow_gridShown_drawsNoSecondTitle() =
        show(NftGramHintsUiState(rows = listOf(NftVersionHint(TonWalletVersion.W5, 1))), gridShown = true) {
            onAllNodesWithText("Collections").assertCountEquals(0)
            onNodeWithText("The GRAM W5 wallet has 1 NFT").assertExists()
        }

    @Test
    fun versionRow_balanceHiddenSingleNft_masksCountAndPluralForm() =
        show(NftGramHintsUiState(rows = listOf(NftVersionHint(TonWalletVersion.W5, 1)), hidden = true)) {
            onNodeWithText("The GRAM W5 wallet has *** NFTs").assertExists()
        }

    @Test
    fun versionRow_addAndCross_reportThatVersion() =
        show(NftGramHintsUiState(rows = listOf(NftVersionHint(TonWalletVersion.W5, 3)))) {
            onNodeWithText("Add").performClick()
            onNodeWithContentDescription("Close").performClick()

            assertEquals(listOf("add W5", "dismiss W5"), events)
        }
}
