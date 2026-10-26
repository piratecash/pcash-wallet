package cash.p.terminal.modules.transactionInfo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.transactions.AmlStatus
import cash.p.terminal.modules.transactions.riskColor
import cash.p.terminal.modules.transactions.riskTextRes
import cash.p.terminal.ui_compose.components.HsRadioButton
import cash.p.terminal.ui_compose.BottomSheetHeader
import cash.p.terminal.ui_compose.TransparentModalBottomSheet
import cash.p.terminal.ui_compose.components.ButtonPrimaryTransparent
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.D1
import cash.p.terminal.ui_compose.components.HeaderText
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlinx.coroutines.launch
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.address_checker_choose_address
import cash.p.terminal.resources.address_checker_title
import cash.p.terminal.resources.aml_check_info_later
import cash.p.terminal.resources.ic_radar_24

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmlAddressSelectionBottomSheet(
    addresses: List<Pair<String, AmlStatus>>,
    onAddressSelected: (String) -> Unit,
    onLaterClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedAddress by remember { mutableStateOf<String?>(null) }

    TransparentModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        AmlAddressSelectionContent(
            addresses = addresses,
            selectedAddress = selectedAddress,
            onAddressClick = { selectedAddress = it },
            onCheckClick = {
                selectedAddress?.let { address ->
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        onAddressSelected(address)
                    }
                }
            },
            onLaterClick = {
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    onLaterClick()
                }
            },
            onCloseClick = {
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    onDismiss()
                }
            }
        )
    }
}

@Composable
internal fun AmlAddressSelectionContent(
    addresses: List<Pair<String, AmlStatus>>,
    selectedAddress: String?,
    onAddressClick: (String) -> Unit,
    onCheckClick: () -> Unit,
    onLaterClick: () -> Unit,
    onCloseClick: () -> Unit
) {
    BottomSheetHeader(
        iconPainter = painterResource(Res.drawable.ic_radar_24),
        iconTint = ColorFilter.tint(ComposeAppTheme.colors.iconSecondary),
        title = stringResource(Res.string.address_checker_title),
        titleColor = ComposeAppTheme.colors.textPrimary,
        onCloseClick = onCloseClick
    ) {
        Column {
            Spacer(Modifier.height(12.dp))
            HeaderText(
                text = stringResource(Res.string.address_checker_choose_address)
            )
            Section(
                items = addresses,
                selectedItem = selectedAddress,
                onSelectListener = onAddressClick,
            )
            VSpacer(16.dp)

            ButtonPrimaryYellow(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(Res.string.address_checker_title),
                enabled = selectedAddress != null,
                onClick = onCheckClick
            )

            ButtonPrimaryTransparent(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                title = stringResource(Res.string.aml_check_info_later),
                onClick = onLaterClick
            )

            VSpacer(32.dp)
        }
    }
}

@Composable
private fun Section(
    items: List<Pair<String, AmlStatus>>,
    selectedItem: String?,
    onSelectListener: (String) -> Unit,
) {
    CellUniversalLawrenceSection(items, showFrame = true) { (address, amlStatus) ->
        RowUniversal(
            modifier = Modifier.padding(horizontal = 16.dp),
            onClick = {
                onSelectListener.invoke(address)
            },
        ) {
            HsRadioButton(
                selected = address == selectedItem,
                onClick = {
                    onSelectListener.invoke(address)
                }
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                body_leah(
                    text = address,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis
                )
                D1(
                    text = stringResource(amlStatus.riskTextRes),
                    textColor = amlStatus.riskColor()
                )
            }
        }
    }
}

