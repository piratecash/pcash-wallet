package cash.p.terminal.modules.nft.gram

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.nft.NftGramHintsUiState
import cash.p.terminal.modules.nft.NftVersionHint
import cash.p.terminal.resources.Button_Add
import cash.p.terminal.resources.Button_Close
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_close_24
import cash.p.terminal.resources.ic_info_20
import cash.p.terminal.resources.ic_nft_image_22
import cash.p.terminal.resources.nft_collections
import cash.p.terminal.resources.nft_gram_add_hint
import cash.p.terminal.resources.nft_gram_info_title
import cash.p.terminal.resources.nft_gram_version_hint
import cash.p.terminal.ui_compose.components.ButtonPrimary
import cash.p.terminal.ui_compose.components.ButtonPrimaryDefaults
import cash.p.terminal.ui_compose.components.headline1_grey
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.entities.TonWalletVersion
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private const val HIDDEN_COUNT = "***"

// A hidden count must not pick the plural form either, or "*** NFT" would reveal exactly one.
private const val HIDDEN_PLURAL_QUANTITY = 100

/**
 * Version rows and the add-GRAM block of the main screen. [gridShown] means the collections section
 * above already drew its own «Коллекции» title.
 */
fun LazyListScope.nftGramHints(
    state: NftGramHintsUiState,
    gridShown: Boolean,
    onInfo: () -> Unit,
    onAdd: (TonWalletVersion) -> Unit,
    onAddDefault: () -> Unit,
    onDismiss: (TonWalletVersion) -> Unit,
) {
    if (state.rows.isEmpty() && !state.showAddGramBlock) return
    item(key = "nft_gram_hints") {
        Column(
            modifier = Modifier.padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!gridShown) NftGramHintsTitle(onInfo = onInfo.takeIf { state.showAddGramBlock })
            state.rows.forEach { row ->
                NftGramHintCard(
                    text = row.text(state.hidden),
                    onAdd = { onAdd(row.version) },
                    onDismiss = { onDismiss(row.version) },
                )
            }
            if (state.showAddGramBlock) {
                NftGramHintCard(text = stringResource(Res.string.nft_gram_add_hint), onAdd = onAddDefault)
            }
        }
    }
}

@Composable
private fun NftVersionHint.text(hidden: Boolean) = pluralStringResource(
    Res.plurals.nft_gram_version_hint,
    if (hidden) HIDDEN_PLURAL_QUANTITY else count,
    version.title,
    if (hidden) HIDDEN_COUNT else count.toString(),
)

@Composable
private fun NftGramHintsTitle(onInfo: (() -> Unit)?) {
    Row(
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        headline1_grey(text = stringResource(Res.string.nft_collections))
        if (onInfo != null) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onInfo),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_info_20),
                    contentDescription = stringResource(Res.string.nft_gram_info_title),
                    tint = ComposeAppTheme.colors.iconSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun NftGramHintCard(text: String, onAdd: () -> Unit, onDismiss: (() -> Unit)? = null) {
    val colors = ComposeAppTheme.colors
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .dashedBorder(colors.borderDefault)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(colors.surfacePrimary)
                .border(1.dp, colors.borderDefault, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_nft_image_22),
                contentDescription = null,
                tint = colors.iconSecondary,
                modifier = Modifier.size(22.dp),
            )
        }
        subhead2_grey(text = text, modifier = Modifier.weight(1f))
        AddButton(onAdd)
        onDismiss?.let { DismissButton(it) }
    }
}

@Composable
private fun AddButton(onClick: () -> Unit) {
    ButtonPrimary(
        onClick = onClick,
        modifier = Modifier.height(36.dp),
        buttonColors = ButtonPrimaryDefaults.filledColors(
            background = ComposeAppTheme.colors.brandDefault,
            content = ComposeAppTheme.colors.buttonPrimaryBrandContent,
        ),
        contentPadding = PaddingValues(horizontal = 14.dp),
    ) {
        Text(
            text = stringResource(Res.string.Button_Add),
            style = ComposeAppTheme.typography.subhead1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DismissButton(onClick: () -> Unit) {
    Icon(
        painter = painterResource(Res.drawable.ic_close_24),
        contentDescription = stringResource(Res.string.Button_Close),
        tint = ComposeAppTheme.colors.iconSecondary,
        modifier = Modifier.size(20.dp).clip(CircleShape).clickable(onClick = onClick),
    )
}

private fun Modifier.dashedBorder(color: Color) = drawBehind {
    val stroke = 1.dp.toPx()
    val dash = 4.dp.toPx()
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(12.dp.toPx()),
        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))),
    )
}

@Composable
private fun HintsPreview(state: NftGramHintsUiState, gridShown: Boolean = false) {
    ComposeAppTheme {
        LazyColumn(
            modifier = Modifier.background(ComposeAppTheme.colors.backgroundBase),
        ) {
            nftGramHints(state, gridShown, onInfo = {}, onAdd = {}, onAddDefault = {}, onDismiss = {})
        }
    }
}

@Preview
@Composable
private fun NftGramAddBlockPreview() = HintsPreview(NftGramHintsUiState(showAddGramBlock = true))

@Preview
@Composable
private fun NftGramVersionRowsPreview() = HintsPreview(
    NftGramHintsUiState(
        rows = listOf(NftVersionHint(TonWalletVersion.V4R2, 1), NftVersionHint(TonWalletVersion.W5, 12)),
    ),
)

@Preview
@Composable
private fun NftGramVersionRowHiddenPreview() = HintsPreview(
    NftGramHintsUiState(rows = listOf(NftVersionHint(TonWalletVersion.W5, 3)), hidden = true),
    gridShown = true,
)
