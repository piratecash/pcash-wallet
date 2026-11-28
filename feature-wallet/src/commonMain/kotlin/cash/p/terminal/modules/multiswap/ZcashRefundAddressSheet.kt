package cash.p.terminal.modules.multiswap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.manageaccount.ui.rememberCopyWithHud
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_attention_20
import cash.p.terminal.resources.ic_copy_20
import cash.p.terminal.resources.zec_refund_address_copy
import cash.p.terminal.resources.zec_refund_address_explorer
import cash.p.terminal.resources.zec_refund_address_title
import cash.p.terminal.resources.zec_refund_notice_direct
import cash.p.terminal.resources.zec_refund_notice_restore
import cash.p.terminal.resources.zec_refund_notice_via_pcash
import cash.p.terminal.resources.zec_refund_plate
import cash.p.terminal.strings.helpers.shorten
import cash.p.terminal.ui_compose.BottomSheetHeader
import cash.p.terminal.ui_compose.TransparentModalBottomSheet
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.ButtonSecondaryCircle
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.LocalUrlOpener
import cash.p.terminal.ui_compose.components.TextImportantWarning
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.subhead2_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ZcashRefundPlate(address: String, onInfoClick: () -> Unit, modifier: Modifier = Modifier) {
    TextImportantWarning(modifier = modifier, icon = Res.drawable.ic_attention_20, onInfoClick = onInfoClick) {
        subhead2_leah(text = stringResource(Res.string.zec_refund_plate, address.shorten()))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ZcashRefundAddressSheet(notice: ZcashRefundNotice, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val urlOpener = LocalUrlOpener.current
    val copyWithHud = rememberCopyWithHud()

    TransparentModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        ZcashRefundAddressContent(
            notice = notice,
            onCopy = { copyWithHud(notice.address) },
            onOpenExplorer = { urlOpener.open("https://blockchair.com/zcash/address/${notice.address}") },
            onClose = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } },
        )
    }
}

@Composable
private fun ZcashRefundAddressContent(
    notice: ZcashRefundNotice,
    onCopy: () -> Unit,
    onOpenExplorer: () -> Unit,
    onClose: () -> Unit,
) {
    val reasonRes = if (notice.viaPcashServer) {
        Res.string.zec_refund_notice_via_pcash
    } else {
        Res.string.zec_refund_notice_direct
    }
    BottomSheetHeader(title = stringResource(Res.string.zec_refund_address_title), onCloseClick = onClose) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ComposeAppTheme.colors.surfacePrimary)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = notice.address.shorten(),
                style = ComposeAppTheme.typography.body,
                color = ComposeAppTheme.colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            HSpacer(16.dp)
            ButtonSecondaryCircle(
                icon = Res.drawable.ic_copy_20,
                contentDescription = stringResource(Res.string.zec_refund_address_copy),
                onClick = onCopy,
            )
        }
        VSpacer(height = 16.dp)
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            subhead2_grey(text = stringResource(reasonRes, notice.providerTitle))
            VSpacer(height = 10.dp)
            subhead2_grey(text = stringResource(Res.string.zec_refund_notice_restore))
        }
        VSpacer(height = 20.dp)
        ButtonPrimaryYellow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            title = stringResource(Res.string.zec_refund_address_explorer),
            onClick = onOpenExplorer,
        )
        VSpacer(height = 32.dp)
    }
}

private const val PREVIEW_ADDRESS = "t1L2b66MXbgpVMXDfUa94GCBFAN4dCxGohM"

@Preview
@Composable
private fun ZcashRefundPlatePreview() {
    ComposeAppTheme {
        ZcashRefundPlate(address = PREVIEW_ADDRESS, onInfoClick = {}, modifier = Modifier.padding(16.dp))
    }
}

@Preview
@Composable
private fun ZcashRefundAddressContentDirectPreview() {
    ComposeAppTheme {
        ZcashRefundAddressContent(
            notice = ZcashRefundNotice(PREVIEW_ADDRESS, "ChangeNow", viaPcashServer = false),
            onCopy = {},
            onOpenExplorer = {},
            onClose = {},
        )
    }
}

@Preview
@Composable
private fun ZcashRefundAddressContentBackendPreview() {
    ComposeAppTheme {
        ZcashRefundAddressContent(
            notice = ZcashRefundNotice(PREVIEW_ADDRESS, "ChangeNow", viaPcashServer = true),
            onCopy = {},
            onOpenExplorer = {},
            onClose = {},
        )
    }
}
