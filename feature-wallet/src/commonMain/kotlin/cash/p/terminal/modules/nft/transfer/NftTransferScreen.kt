package cash.p.terminal.modules.nft.transfer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cash.p.terminal.entities.NftItemRecord
import cash.p.terminal.modules.nft.nftCard
import cash.p.terminal.modules.nft.ui.NftArtwork
import cash.p.terminal.modules.nft.ui.displayName
import cash.p.terminal.modules.nft.ui.previewNftItem
import cash.p.terminal.resources.Button_Continue
import cash.p.terminal.resources.FeeSettings_NetworkFee
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.TonConnect_Wallet
import cash.p.terminal.resources.ic_checkmark_20
import cash.p.terminal.resources.nft_transfer_address_found
import cash.p.terminal.resources.nft_transfer_comment_hint
import cash.p.terminal.resources.nft_transfer_comment_label
import cash.p.terminal.resources.nft_transfer_domain_warning
import cash.p.terminal.resources.nft_transfer_title
import cash.p.terminal.resources.nft_transfer_ton_balance
import cash.p.terminal.resources.nft_unnamed_collection
import cash.p.terminal.resources.offline_transaction_recipient
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui.compose.components.FormsInput
import cash.p.terminal.ui.compose.components.PoisonAddressRiskSection
import cash.p.terminal.ui.compose.components.PoisonWarningCell
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.SectionUniversalItem
import cash.p.terminal.ui_compose.components.TextImportantError
import cash.p.terminal.ui_compose.components.TextImportantWarningInline
import cash.p.terminal.ui_compose.components.TitleAndValueCell
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.entities.DataState
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

internal class NftTransferScreenActions(
    val onRiskAcceptedChange: (Boolean) -> Unit,
    val onCommentChange: (String) -> Unit,
    val onContinue: () -> Unit,
    val onBack: () -> Unit,
)

@Composable
internal fun NftTransferScreen(
    item: NftItemRecord,
    nftDomain: String?,
    uiState: NftTransferUiState,
    canContinue: Boolean,
    recipientInput: @Composable () -> Unit,
    actions: NftTransferScreenActions,
) {
    Scaffold(
        topBar = {
            AppBar(
                title = stringResource(Res.string.nft_transfer_title),
                navigationIcon = { HsBackButton(onClick = actions.onBack) },
            )
        },
        containerColor = ComposeAppTheme.colors.backgroundBase,
    ) { paddingValues ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
        ) {
            TransferForm(
                modifier = Modifier.weight(1f),
                item = item,
                nftDomain = nftDomain,
                uiState = uiState,
                recipientInput = recipientInput,
                actions = actions,
            )
            ButtonPrimaryYellow(
                modifier = Modifier
                    .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp)
                    .fillMaxWidth(),
                title = stringResource(Res.string.Button_Continue),
                enabled = canContinue,
                onClick = actions.onContinue,
            )
        }
    }
}

@Composable
private fun TransferForm(
    item: NftItemRecord,
    nftDomain: String?,
    uiState: NftTransferUiState,
    recipientInput: @Composable () -> Unit,
    actions: NftTransferScreenActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        NftAssetRow(
            item = item,
            modifier = Modifier
                .nftCard()
                .background(ComposeAppTheme.colors.surfacePrimary)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
        RecipientBlock(uiState, recipientInput, actions.onRiskAcceptedChange)
        CommentBlock(uiState, actions.onCommentChange)
        Summary(uiState)
        uiState.insufficientBalance?.let { TextImportantError(text = it.getString()) }
        nftDomain?.let {
            TextImportantWarningInline(text = stringResource(Res.string.nft_transfer_domain_warning, it))
        }
    }
}

@Composable
internal fun NftAssetRow(item: NftItemRecord, modifier: Modifier = Modifier) {
    val collection = item.collectionName.takeUnless { it.isNullOrBlank() }
        ?: stringResource(Res.string.nft_unnamed_collection)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NftArtwork(
            url = item.imageUrl,
            hidden = false,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp)),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = item.displayName(hidden = false),
                style = ComposeAppTheme.typography.headline2,
                color = ComposeAppTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subhead2_grey(text = collection, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun RecipientBlock(
    uiState: NftTransferUiState,
    recipientInput: @Composable () -> Unit,
    onRiskAcceptedChange: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        subhead2_grey(text = stringResource(Res.string.offline_transaction_recipient))
        if (uiState.isPoisonAddress) PoisonWarningCell()
        recipientInput()
        uiState.recipientDomain?.let { ResolvedDomainLine(it) }
        PoisonAddressRiskSection(
            isPoisonAddress = uiState.isPoisonAddress,
            riskAccepted = uiState.riskAccepted,
            onRiskAcceptedChange = onRiskAcceptedChange,
        )
    }
}

@Composable
private fun ResolvedDomainLine(domain: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_checkmark_20),
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = ComposeAppTheme.colors.statusSuccess,
        )
        Text(
            text = stringResource(Res.string.nft_transfer_address_found, domain),
            style = ComposeAppTheme.typography.caption.copy(lineHeight = 16.sp),
            color = ComposeAppTheme.colors.statusSuccess,
        )
    }
}

@Composable
private fun CommentBlock(uiState: NftTransferUiState, onCommentChange: (String) -> Unit) {
    val error = uiState.commentError?.getString()?.let { DataState.Error(Exception(it)) }
    // FormsInput is one text line plus 12dp paddings, so this line gives the designed 56dp field.
    val style = ComposeAppTheme.typography.body.copy(
        lineHeight = 32.sp,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        subhead2_grey(text = stringResource(Res.string.nft_transfer_comment_label))
        FormsInput(
            initial = uiState.comment,
            hint = stringResource(Res.string.nft_transfer_comment_hint),
            textStyle = style,
            hintStyle = style,
            state = error,
            pasteEnabled = false,
            singleLine = true,
            maxBytes = NFT_COMMENT_MAX_BYTES,
            onValueChange = onCommentChange,
        )
    }
}

@Composable
private fun Summary(uiState: NftTransferUiState) {
    val rows = listOf(
        stringResource(Res.string.TonConnect_Wallet) to uiState.walletLabel,
        stringResource(Res.string.FeeSettings_NetworkFee) to uiState.fee,
        stringResource(Res.string.nft_transfer_ton_balance) to uiState.balance,
    )
    Column(Modifier.nftCard()) {
        rows.forEachIndexed { index, (title, value) ->
            SectionUniversalItem(borderTop = index != 0) {
                TitleAndValueCell(title = title, value = value, minHeight = 48.dp)
            }
        }
    }
}

private val previewState = NftTransferUiState(
    walletLabel = "My wallet · UQAx…7f3k",
    recipientDomain = null,
    addressError = null,
    isPoisonAddress = false,
    riskAccepted = false,
    comment = "",
    commentError = null,
    fee = "---",
    balance = "128.45 TON",
    insufficientBalance = null,
)

@Composable
private fun PreviewContent(
    uiState: NftTransferUiState,
    recipient: String = "",
    nftDomain: String? = null,
    canContinue: Boolean = false,
) {
    ComposeAppTheme {
        NftTransferScreen(
            item = previewNftItem("EQBx"),
            nftDomain = nftDomain,
            uiState = uiState,
            canContinue = canContinue,
            recipientInput = { FormsInput(initial = recipient, hint = "Address or .ton domain", onValueChange = {}) },
            actions = NftTransferScreenActions(
                onRiskAcceptedChange = {},
                onCommentChange = {},
                onContinue = {},
                onBack = {},
            ),
        )
    }
}

@Preview
@Composable
private fun NftTransferScreenEmptyPreview() = PreviewContent(previewState)

@Preview
@Composable
private fun NftTransferScreenFilledPreview() = PreviewContent(
    uiState = previewState.copy(fee = "≈ 0.05 TON · $0.25"),
    recipient = "UQCOwnerAddress0000000000000000000000000000000000",
    canContinue = true,
)

@Preview
@Composable
private fun NftTransferScreenDomainPreview() = PreviewContent(
    uiState = previewState.copy(recipientDomain = "alex.ton", fee = "≈ 0.05 TON · $0.25"),
    recipient = "alex.ton",
    nftDomain = "piratecash.ton",
    canContinue = true,
)

@Preview
@Composable
private fun NftTransferScreenInsufficientPreview() = PreviewContent(
    uiState = previewState.copy(
        fee = "≈ 0.05 TON · $0.25",
        balance = "0.01 TON",
        insufficientBalance = TranslatableString.PlainString("Not enough TON: 0.06 TON required, 0.01 TON available."),
    ),
    recipient = "UQCOwnerAddress0000000000000000000000000000000000",
)
