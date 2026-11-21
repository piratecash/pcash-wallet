package cash.p.terminal.modules.nft.transfer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.nft.operation.NftInfoRow
import cash.p.terminal.modules.nft.operation.NftRowsCard
import cash.p.terminal.navigation.HSBottomSheet
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_attention_24
import cash.p.terminal.resources.nft_trap_dont_send
import cash.p.terminal.resources.nft_trap_outcome_header
import cash.p.terminal.resources.nft_trap_row_loss
import cash.p.terminal.resources.nft_trap_row_nft
import cash.p.terminal.resources.nft_trap_row_nft_value
import cash.p.terminal.resources.nft_trap_row_recipient
import cash.p.terminal.resources.nft_trap_row_recipient_value
import cash.p.terminal.resources.nft_trap_send_anyway
import cash.p.terminal.resources.nft_trap_sheet_body
import cash.p.terminal.resources.nft_trap_sheet_title
import cash.p.terminal.ui_compose.BottomSheetHeader
import cash.p.terminal.ui_compose.ColorName
import cash.p.terminal.ui_compose.components.ButtonPrimaryTransparent
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.TextImportantError
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Asks once more before sending an NFT the emulation shows will not move; a [Result] means "send anyway". */
class NftTrapWarningSheet(private val nftName: String, private val attached: String) : HSBottomSheet() {

    class Result

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        NftTrapWarningSheetContent(
            nftName = nftName,
            attached = attached,
            onDontSend = navigation::navigateUpSafely,
            onSendAnyway = {
                navigation.setResult(this, Result())
                navigation.navigateUpSafely()
            },
        )
    }
}

@Composable
internal fun NftTrapWarningSheetContent(
    nftName: String,
    attached: String,
    onDontSend: () -> Unit,
    onSendAnyway: () -> Unit,
) {
    val error = ComposeAppTheme.colors.statusError
    BottomSheetHeader(
        title = stringResource(Res.string.nft_trap_sheet_title),
        onCloseClick = onDontSend,
        iconPainter = painterResource(Res.drawable.ic_attention_24),
        iconTint = ColorFilter.tint(error),
        closeIconTint = error,
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextImportantError(text = stringResource(Res.string.nft_trap_sheet_body, nftName, attached))
            subhead2_grey(
                text = stringResource(Res.string.nft_trap_outcome_header),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            NftRowsCard(
                listOf(
                    NftInfoRow(
                        stringResource(Res.string.nft_trap_row_recipient),
                        stringResource(Res.string.nft_trap_row_recipient_value),
                    ),
                    NftInfoRow(
                        stringResource(Res.string.nft_trap_row_nft, nftName),
                        stringResource(Res.string.nft_trap_row_nft_value),
                    ),
                    NftInfoRow(stringResource(Res.string.nft_trap_row_loss), attached, ColorName.Lucian),
                )
            )
        }
        Column(
            modifier = Modifier.padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ButtonPrimaryYellow(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(Res.string.nft_trap_dont_send),
                onClick = onDontSend,
            )
            ButtonPrimaryTransparent(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(Res.string.nft_trap_send_anyway),
                textColor = error,
                onClick = onSendAnyway,
            )
        }
    }
}

@Preview
@Composable
private fun NftTrapWarningSheetPreview() {
    ComposeAppTheme {
        NftTrapWarningSheetContent(nftName = "#1148", attached = "0.05 TON", onDontSend = {}, onSendAnyway = {})
    }
}
