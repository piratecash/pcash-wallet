package cash.p.terminal.modules.nft

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.modules.nft.operation.NftConfirmSheetContent
import cash.p.terminal.modules.nft.operation.NftConfirmStyle
import cash.p.terminal.navigation.HSBottomSheet
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_report_24
import cash.p.terminal.resources.nft_report_body
import cash.p.terminal.resources.nft_report_confirm
import cash.p.terminal.resources.nft_report_title
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.stringResource

/** Confirms marking the collection called [collectionName] as spam; closes itself with [Confirmed]. */
class NftReportSpamSheet(private val collectionName: String) : HSBottomSheet() {

    data object Confirmed

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        NftReportSpamSheetContent(
            collectionName = collectionName,
            onConfirm = {
                navigation.setResult(this, Confirmed)
                navigation.navigateUpSafely()
            },
            onCancel = navigation::navigateUpSafely,
        )
    }
}

@Composable
internal fun NftReportSpamSheetContent(collectionName: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    NftConfirmSheetContent(
        icon = Res.drawable.ic_report_24,
        title = stringResource(Res.string.nft_report_title),
        body = stringResource(Res.string.nft_report_body, collectionName),
        rows = emptyList(),
        confirmTitle = stringResource(Res.string.nft_report_confirm),
        confirmStyle = NftConfirmStyle.Destructive,
        onConfirm = onConfirm,
        onCancel = onCancel,
    )
}

@Preview
@Composable
private fun NftReportSpamSheetPreview() {
    ComposeAppTheme {
        NftReportSpamSheetContent(collectionName = "WIN 5000 USDT", onConfirm = {}, onCancel = {})
    }
}
