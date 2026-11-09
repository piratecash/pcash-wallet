package cash.p.terminal.modules.nft.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cash.p.terminal.entities.NftItemRecord
import cash.p.terminal.entities.NftTrust
import cash.p.terminal.modules.transactions.supportsBlur
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.nft_artwork_placeholder
import cash.p.terminal.resources.nft_unnamed
import cash.p.terminal.resources.nft_unnamed_collection
import cash.p.terminal.resources.nft_unverified
import cash.p.terminal.ui_compose.components.HsImage
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.core.entities.BlockchainType
import org.jetbrains.compose.resources.stringResource

private const val HIDDEN_NAME = "****"
internal const val HIDDEN_COLLECTION = "***"

@Composable
fun NftCard(
    item: NftItemRecord,
    hidden: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ComposeAppTheme.colors.surfacePrimary)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(ComposeAppTheme.colors.surfacePlaceholder)
        ) {
            NftArtwork(url = item.imageUrl, hidden = hidden)
        }
        Column(
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = item.displayName(hidden),
                style = ComposeAppTheme.typography.subhead1.copy(fontWeight = FontWeight.SemiBold, lineHeight = 18.sp),
                color = ComposeAppTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            CollectionLine(item = item, hidden = hidden)
        }
    }
}

@Composable
internal fun NftItemRecord.displayName(hidden: Boolean): String =
    if (hidden) HIDDEN_NAME else name.takeUnless { it.isNullOrBlank() } ?: stringResource(Res.string.nft_unnamed)

/** Dimming leaves the artwork recognizable, so without blur support the image is not loaded at all. */
@Composable
internal fun NftArtwork(
    url: String?,
    hidden: Boolean,
    modifier: Modifier = Modifier,
    alternativeUrl: String? = null,
) {
    val concealed = hidden && !supportsBlur
    HsImage(
        url = url.takeUnless { concealed },
        alternativeUrl = alternativeUrl.takeUnless { concealed },
        placeholder = Res.drawable.nft_artwork_placeholder,
        modifier = modifier
            .fillMaxSize()
            .then(if (hidden && supportsBlur) Modifier.blur(14.dp) else Modifier),
        contentScale = ContentScale.Crop,
        showAlternativeWhileLoading = true,
    )
}

@Composable
private fun CollectionLine(item: NftItemRecord, hidden: Boolean) {
    val unverified = item.trust == NftTrust.None
    val text = when {
        hidden -> HIDDEN_COLLECTION
        unverified -> stringResource(Res.string.nft_unverified)
        item.collectionName.isNullOrBlank() -> stringResource(Res.string.nft_unnamed_collection)
        else -> item.collectionName.orEmpty()
    }
    Text(
        text = text,
        style = ComposeAppTheme.typography.caption.copy(lineHeight = 16.sp),
        color = if (unverified && !hidden) {
            ComposeAppTheme.colors.statusWarning
        } else {
            ComposeAppTheme.colors.textSecondary
        },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

internal fun previewNftItem(
    address: String,
    name: String? = "Getgems Cat #$address",
    collectionName: String? = "Cool Cats",
    trust: NftTrust = NftTrust.Whitelist,
) = NftItemRecord(
    accountId = "account",
    blockchainType = BlockchainType.Ton,
    ownerAddress = "owner",
    address = address,
    position = 0,
    name = name,
    description = null,
    realOwnerAddress = null,
    collectionAddress = "collection",
    collectionName = collectionName,
    collectionDescription = null,
    trust = trust,
    verified = false,
    imageUrl = null,
    largeImageUrl = null,
)

@Preview
@Composable
private fun NftCardPreview() {
    ComposeAppTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            NftCard(previewNftItem("1"), hidden = false, onClick = {}, modifier = Modifier.padding(bottom = 8.dp))
            NftCard(
                previewNftItem("2", collectionName = null, trust = NftTrust.None),
                hidden = false,
                onClick = {},
                modifier = Modifier.padding(bottom = 8.dp)
            )
            NftCard(previewNftItem("3", name = null, collectionName = " "), hidden = false, onClick = {})
        }
    }
}

@Preview
@Composable
private fun NftCardHiddenPreview() {
    ComposeAppTheme {
        NftCard(
            previewNftItem("1", trust = NftTrust.None),
            hidden = true,
            onClick = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}
