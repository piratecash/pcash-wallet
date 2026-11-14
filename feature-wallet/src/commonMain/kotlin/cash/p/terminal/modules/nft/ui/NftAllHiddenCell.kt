package cash.p.terminal.modules.nft.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_arrow_right
import cash.p.terminal.resources.ic_eye_off
import cash.p.terminal.resources.nft_all_hidden
import cash.p.terminal.resources.nft_configure
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun NftAllHiddenCell(onManage: () -> Unit, modifier: Modifier = Modifier) {
    NftHiddenEntryCell(
        label = stringResource(Res.string.nft_all_hidden),
        actionTitle = stringResource(Res.string.nft_configure),
        onClick = onManage,
        modifier = modifier.height(56.dp),
    )
}

/** Entry to collection management shown when some or all collections are hidden. */
@Composable
internal fun NftHiddenEntryCell(
    label: String,
    actionTitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showChevron: Boolean = false,
) {
    val colors = ComposeAppTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surfacePrimary)
            .border(1.dp, colors.borderDefault, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_eye_off),
            contentDescription = null,
            tint = colors.iconSecondary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            style = ComposeAppTheme.typography.subhead2,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = actionTitle,
            style = ComposeAppTheme.typography.subhead2.copy(fontWeight = FontWeight.Medium),
            color = colors.brandDefault,
        )
        if (showChevron) {
            Icon(
                painter = painterResource(Res.drawable.ic_arrow_right),
                contentDescription = null,
                tint = colors.brandDefault,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Preview
@Composable
private fun NftAllHiddenCellPreview() {
    ComposeAppTheme {
        NftAllHiddenCell(onManage = {}, modifier = Modifier.padding(16.dp))
    }
}
