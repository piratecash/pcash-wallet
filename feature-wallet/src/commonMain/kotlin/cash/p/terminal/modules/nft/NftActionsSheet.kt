package cash.p.terminal.modules.nft

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.entities.NftTrust
import cash.p.terminal.navigation.HSBottomSheet
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.ui_compose.components.BottomSheetSurface
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Menu of the NFT preview; closes itself with the picked [Result]. */
class NftActionsSheet(private val actions: List<NftAction>) : HSBottomSheet() {

    class Result(val action: NftAction)

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        NftActionsSheetContent(actions) {
            navigation.setResult(this, Result(it))
            navigation.navigateUpSafely()
        }
    }
}

@Composable
internal fun NftActionsSheetContent(actions: List<NftAction>, onPick: (NftAction) -> Unit) {
    BottomSheetSurface {
        Column(Modifier.padding(top = 8.dp, bottom = 32.dp)) {
            actions.forEach { ActionRow(it, onClick = { onPick(it) }) }
        }
    }
}

@Composable
private fun ActionRow(action: NftAction, onClick: () -> Unit) {
    val colors = ComposeAppTheme.colors
    val (iconTint, textColor) =
        if (action.destructive) colors.statusError to colors.statusError else colors.brandDefault to colors.textPrimary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(action.icon),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                colorFilter = ColorFilter.tint(iconTint),
            )
        }
        Text(
            text = stringResource(action.title),
            style = ComposeAppTheme.typography.body.copy(fontWeight = FontWeight.Medium),
            color = textColor,
        )
    }
}

@Composable
private fun PreviewContent(actions: List<NftAction>) {
    ComposeAppTheme { NftActionsSheetContent(actions = actions, onPick = {}) }
}

@Preview
@Composable
private fun NftActionsSheetVerifiedPreview() = PreviewContent(
    nftActions(NftTrust.Whitelist, hasCollection = true, canBurn = false, hasExplorer = true),
)

@Preview
@Composable
private fun NftActionsSheetUnverifiedPreview() = PreviewContent(
    nftActions(NftTrust.None, hasCollection = true, canBurn = true, hasExplorer = true),
)
