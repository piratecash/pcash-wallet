package cash.p.terminal.modules.nft.manage

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import cash.p.terminal.core.nft.NftCollectionState
import cash.p.terminal.entities.NftItemRecord
import cash.p.terminal.entities.NftTrust
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.core.entities.BlockchainType
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class NftManageScreenDesktopTest {

    private fun row(collection: String?, name: String?, state: NftCollectionState) = NftManageRow(
        item = NftItemRecord(
            accountId = "account",
            blockchainType = BlockchainType.Ton,
            ownerAddress = "owner",
            address = "nft-$collection-$name",
            position = 0,
            name = name,
            description = null,
            realOwnerAddress = null,
            collectionAddress = collection,
            collectionName = collection?.let { "Collection $it" },
            collectionDescription = null,
            trust = NftTrust.Whitelist,
            verified = false,
            imageUrl = "https://example.com/art.png",
            largeImageUrl = null,
        ),
        nftCount = 1,
        state = state,
    )

    private fun showRows(vararg rows: NftManageRow, assertions: ComposeUiTest.() -> Unit) =
        runDesktopComposeUiTest {
            setContent {
                ComposeAppTheme {
                    NftManageScreen(
                        uiState = NftManageUiState(rows = rows.toList(), isLoading = false),
                        onVisibleChange = { _, _, _ -> },
                        onNotSpam = { _, _ -> },
                        onToggleShowAll = {},
                        onBack = {},
                    )
                }
            }
            assertions()
        }

    @Test
    fun rowIcon_spamCollection_showsWarningGlyphInsteadOfArtwork() =
        showRows(row("c1", "NFT", NftCollectionState.SpamByProvider)) { onNodeWithText("!").assertExists() }

    @Test
    fun rowIcon_unnamedSingleNft_showsQuestionGlyph() =
        showRows(row(null, null, NftCollectionState.Visible)) { onNodeWithText("?").assertExists() }

    @Test
    fun rowIcon_regularCollection_showsNoGlyph() = showRows(row("c1", "NFT", NftCollectionState.Visible)) {
        onNodeWithText("!").assertDoesNotExist()
        onNodeWithText("?").assertDoesNotExist()
    }
}
