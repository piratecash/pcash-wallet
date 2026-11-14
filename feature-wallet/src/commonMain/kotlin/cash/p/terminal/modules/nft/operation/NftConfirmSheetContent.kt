package cash.p.terminal.modules.nft.operation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.nft.nftCard
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_fire_24
import cash.p.terminal.resources.nft_domain_renew_cancel
import cash.p.terminal.ui_compose.components.BottomSheetSurface
import cash.p.terminal.ui_compose.components.ButtonPrimaryRed
import cash.p.terminal.ui_compose.components.ButtonPrimaryTransparent
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.SectionUniversalItem
import cash.p.terminal.ui_compose.components.TextImportantError
import cash.p.terminal.ui_compose.components.TitleAndValueCell
import cash.p.terminal.ui_compose.components.body_grey
import cash.p.terminal.ui_compose.components.headline1_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

internal enum class NftConfirmStyle { Primary, Destructive }

/** Layout shared by the NFT confirmation sheets: icon, title, body, a card of rows, then confirm and cancel. */
@Composable
internal fun NftConfirmSheetContent(
    icon: DrawableResource,
    title: String,
    body: String,
    rows: List<Pair<String, String>>,
    confirmTitle: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    error: String? = null,
    confirmStyle: NftConfirmStyle = NftConfirmStyle.Primary,
    confirmEnabled: Boolean = true,
    inProgress: Boolean = false,
) {
    BottomSheetSurface {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Header(icon, confirmStyle, title, body)
            if (rows.isNotEmpty()) RowsCard(rows)
            error?.let { TextImportantError(text = it) }
            val confirmModifier = Modifier.fillMaxWidth()
            when (confirmStyle) {
                NftConfirmStyle.Primary -> ButtonPrimaryYellow(
                    modifier = confirmModifier,
                    title = confirmTitle,
                    enabled = confirmEnabled,
                    loadingIndicator = inProgress,
                    onClick = onConfirm,
                )
                NftConfirmStyle.Destructive -> ButtonPrimaryRed(
                    modifier = confirmModifier,
                    title = confirmTitle,
                    enabled = confirmEnabled && !inProgress,
                    loadingIndicator = inProgress,
                    onClick = onConfirm,
                )
            }
            ButtonPrimaryTransparent(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(Res.string.nft_domain_renew_cancel),
                enabled = !inProgress,
                onClick = onCancel,
            )
        }
    }
}

@Composable
private fun Header(
    icon: DrawableResource,
    style: NftConfirmStyle,
    title: String,
    body: String,
) {
    val colors = ComposeAppTheme.colors
    val (iconTint, circleColor) = when (style) {
        NftConfirmStyle.Primary -> colors.brandDefault to colors.brandDefault15
        NftConfirmStyle.Destructive -> colors.statusError to colors.statusError15
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(circleColor),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                colorFilter = ColorFilter.tint(iconTint),
            )
        }
        headline1_leah(text = title, textAlign = TextAlign.Center)
        body_grey(text = body, textAlign = TextAlign.Center)
    }
}

@Composable
private fun RowsCard(rows: List<Pair<String, String>>) {
    Column(Modifier.nftCard()) {
        rows.forEachIndexed { index, (title, value) ->
            SectionUniversalItem(borderTop = index != 0) {
                TitleAndValueCell(title = title, value = value, minHeight = 48.dp)
            }
        }
    }
}

@Composable
private fun PreviewContent(confirmStyle: NftConfirmStyle, inProgress: Boolean = false) {
    ComposeAppTheme {
        NftConfirmSheetContent(
            icon = Res.drawable.ic_fire_24,
            title = "Burn this NFT?",
            body = "The NFT will be sent to the burn address and cannot be returned.",
            rows = listOf("Network fee" to "≈ 0.03 TON · $0.15"),
            confirmTitle = "Confirm",
            onConfirm = {},
            onCancel = {},
            confirmStyle = confirmStyle,
            inProgress = inProgress,
        )
    }
}

@Preview
@Composable
private fun NftConfirmSheetPrimaryPreview() = PreviewContent(NftConfirmStyle.Primary)

@Preview
@Composable
private fun NftConfirmSheetDestructivePreview() = PreviewContent(NftConfirmStyle.Destructive)

@Preview
@Composable
private fun NftConfirmSheetDestructiveInProgressPreview() =
    PreviewContent(NftConfirmStyle.Destructive, inProgress = true)
