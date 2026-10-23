package cash.p.terminal.modules.offline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import cash.p.terminal.resources.Button_Cancel
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.icon_warning_2_20
import cash.p.terminal.resources.offline_mode_confirm_button
import cash.p.terminal.resources.offline_mode_confirm_description
import cash.p.terminal.resources.offline_mode_confirm_description_zcash
import cash.p.terminal.resources.offline_mode_confirm_title
import cash.p.terminal.ui.compose.components.Badge
import cash.p.terminal.ui.compose.components.CoinImage
import cash.p.terminal.ui_compose.BottomSheetHeader
import cash.p.terminal.ui_compose.TransparentModalBottomSheet
import cash.p.terminal.ui_compose.components.ButtonPrimaryTransparent
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.VerticalScrollIndicator
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.plateBackground
import cash.p.terminal.ui_compose.components.subhead2_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val MAX_MEMBERS_LIST_HEIGHT = 280.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineModeConfirmationBottomSheet(
    blockchainName: String,
    isZcash: Boolean,
    members: List<OfflineModeAssetItem>,
    inProgress: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val close: () -> Unit = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } }

    TransparentModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        BottomSheetHeader(
            iconPainter = painterResource(Res.drawable.icon_warning_2_20),
            iconTint = ColorFilter.tint(ComposeAppTheme.colors.statusWarning),
            title = stringResource(Res.string.offline_mode_confirm_title),
            onCloseClick = close,
        ) {
            val description = if (isZcash) {
                stringResource(Res.string.offline_mode_confirm_description_zcash)
            } else {
                stringResource(Res.string.offline_mode_confirm_description).format(blockchainName)
            }
            subhead2_leah(modifier = Modifier.padding(horizontal = 32.dp), text = description)
            VSpacer(24.dp)
            val listState = rememberLazyListState()
            Box {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = MAX_MEMBERS_LIST_HEIGHT),
                    state = listState,
                ) {
                    items(members) { member -> OfflineMemberRow(member) }
                }
                VerticalScrollIndicator(
                    state = listState.scrollIndicatorState,
                    modifier = Modifier.matchParentSize(),
                )
            }
            VSpacer(24.dp)
            ButtonPrimaryYellow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                title = stringResource(Res.string.offline_mode_confirm_button),
                onClick = onConfirm,
                enabled = !inProgress,
                loadingIndicator = inProgress,
            )
            VSpacer(12.dp)
            ButtonPrimaryTransparent(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                title = stringResource(Res.string.Button_Cancel),
                onClick = close,
                enabled = !inProgress,
            )
            VSpacer(32.dp)
        }
    }
}

@Composable
private fun OfflineMemberRow(member: OfflineModeAssetItem) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 4.dp)
            .clip(shape)
            .background(plateBackground())
            .border(1.dp, ComposeAppTheme.colors.borderDefault, shape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoinImage(coin = member.coin, modifier = Modifier.size(24.dp))
        HSpacer(12.dp)
        body_leah(text = member.coin.code)
        member.badge?.let {
            Badge(modifier = Modifier.padding(start = 8.dp), text = it)
        }
        Spacer(Modifier.weight(1f))
        body_leah(text = member.balance)
    }
}
