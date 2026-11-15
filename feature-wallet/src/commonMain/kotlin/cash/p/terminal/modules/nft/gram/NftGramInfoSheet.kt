package cash.p.terminal.modules.nft.gram

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.managewallets.ManageWalletsPage
import cash.p.terminal.navigation.HSBottomSheet
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.navigation.rememberResultLauncher
import cash.p.terminal.navigation.slideFromBottom
import cash.p.terminal.resources.Button_Close
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_info_20
import cash.p.terminal.resources.nft_gram_add_blockchain
import cash.p.terminal.resources.nft_gram_info_text
import cash.p.terminal.resources.nft_gram_info_title
import cash.p.terminal.ui_compose.components.BottomSheetSurface
import cash.p.terminal.ui_compose.components.ButtonPrimaryTransparent
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.body_grey
import cash.p.terminal.ui_compose.components.headline1_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Why NFT collections need GRAM; closes itself with [AddBlockchain] when the user goes to add it. */
class NftGramInfoSheet : HSBottomSheet() {

    data object AddBlockchain

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        NftGramInfoSheetContent(
            onAddBlockchain = {
                navigation.setResult(this, AddBlockchain)
                navigation.navigateUpSafely()
            },
            onClose = navigation::navigateUpSafely,
        )
    }
}

/** Opens the sheet; «Добавить блокчейн» leads to the coin manager. */
@Composable
fun HSNavigation.rememberNftGramInfoOpener(): () -> Unit {
    val launcher = rememberResultLauncher<NftGramInfoSheet.AddBlockchain> { slideFromRight(ManageWalletsPage()) }
    return { launcher.slideFromBottom(NftGramInfoSheet()) }
}

@Composable
internal fun NftGramInfoSheetContent(onAddBlockchain: () -> Unit, onClose: () -> Unit) {
    val colors = ComposeAppTheme.colors
    BottomSheetSurface {
        Column(
            modifier = Modifier.padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(colors.brandDefault15),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_info_20),
                        contentDescription = null,
                        tint = colors.brandDefault,
                        modifier = Modifier.size(22.dp),
                    )
                }
                headline1_leah(text = stringResource(Res.string.nft_gram_info_title))
            }
            body_grey(text = stringResource(Res.string.nft_gram_info_text))
            ButtonPrimaryYellow(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(Res.string.nft_gram_add_blockchain),
                onClick = onAddBlockchain,
            )
            ButtonPrimaryTransparent(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(Res.string.Button_Close),
                onClick = onClose,
            )
        }
    }
}

@Preview
@Composable
private fun NftGramInfoSheetPreview() {
    ComposeAppTheme {
        NftGramInfoSheetContent(onAddBlockchain = {}, onClose = {})
    }
}
