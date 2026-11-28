package cash.p.terminal.modules.balance.token

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cash.p.terminal.resources.Button_Close
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_arrow_right
import cash.p.terminal.resources.zec_address_search_addresses
import cash.p.terminal.resources.zec_address_search_check_all_again
import cash.p.terminal.resources.zec_address_search_checked
import cash.p.terminal.resources.zec_address_search_description
import cash.p.terminal.resources.zec_address_search_found
import cash.p.terminal.resources.zec_address_search_hint
import cash.p.terminal.resources.zec_address_search_info
import cash.p.terminal.resources.zec_address_search_long
import cash.p.terminal.resources.zec_address_search_more
import cash.p.terminal.resources.zec_address_search_progress
import cash.p.terminal.resources.zec_address_search_running
import cash.p.terminal.resources.zec_address_search_start
import cash.p.terminal.resources.zec_address_search_stop
import cash.p.terminal.resources.zec_address_search_title
import cash.p.terminal.resources.zec_address_search_total
import cash.p.terminal.ui_compose.BottomSheetHeader
import cash.p.terminal.ui_compose.TransparentModalBottomSheet
import cash.p.terminal.ui_compose.components.ButtonPrimaryTransparent
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.ButtonSecondaryTransparent
import cash.p.terminal.ui_compose.components.HsDivider
import cash.p.terminal.ui_compose.components.HsSettingCell
import cash.p.terminal.ui_compose.components.InfoBottomSheet
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_grey
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.subhead2_warning
import cash.p.terminal.ui_compose.theme.ColoredTextStyle
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Immutable
data class ZcashAddressSearchUiState(
    val running: Boolean,
    val checked: Int,
    val checkedText: String,
    val checkedDate: String?,
    val targetText: String,
    val percent: Int,
    val foundText: String,
    val moreText: String,
    /** Null while the "check more" input is not a valid count. */
    val total: Int?,
    val totalText: String?,
    val longSearchMinutes: Int?,
)

private enum class AddressSearchSheet { START, RUNNING }

@Composable
internal fun ZcashAddressSearchRow(
    uiState: ZcashAddressSearchUiState,
    onMoreChange: (String) -> Unit,
    onStart: () -> Unit,
    onCheckAllAgain: () -> Unit,
    onStop: () -> Unit,
) {
    // Fixed at tap, so the content does not swap to the other sheet while this one slides away.
    var sheet by remember { mutableStateOf<AddressSearchSheet?>(null) }
    var infoShown by remember { mutableStateOf(false) }
    HsSettingCell(
        title = stringResource(Res.string.zec_address_search_title),
        arrowPainter = painterResource(Res.drawable.ic_arrow_right),
        value = if (uiState.running) {
            stringResource(Res.string.zec_address_search_running, uiState.percent)
        } else {
            checkedAddresses(uiState)
        },
        onClick = { sheet = if (uiState.running) AddressSearchSheet.RUNNING else AddressSearchSheet.START },
        onInfoClick = { infoShown = true },
    )
    if (infoShown) {
        InfoBottomSheet(
            title = stringResource(Res.string.zec_address_search_title),
            text = stringResource(
                Res.string.zec_address_search_info,
                stringResource(Res.string.zec_address_search_check_all_again),
            ),
            onDismiss = { infoShown = false },
        )
    }
    sheet?.let { shown ->
        AddressSearchBottomSheet(onDismiss = { sheet = null }) { close ->
            when (shown) {
                AddressSearchSheet.START -> AddressSearchStartContent(
                    uiState = uiState,
                    onMoreChange = onMoreChange,
                    onStart = { onStart(); close() },
                    onCheckAllAgain = { onCheckAllAgain(); close() },
                    onClose = close,
                )

                AddressSearchSheet.RUNNING -> AddressSearchRunningContent(
                    uiState = uiState,
                    onStop = { onStop(); close() },
                    onClose = close,
                )
            }
        }
    }
}

@Composable
private fun checkedAddresses(uiState: ZcashAddressSearchUiState): String =
    pluralStringResource(Res.plurals.zec_address_search_addresses, uiState.checked, uiState.checkedText)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddressSearchBottomSheet(onDismiss: () -> Unit, content: @Composable (close: () -> Unit) -> Unit) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val close: () -> Unit = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } }
    TransparentModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        // TransparentModalBottomSheet leaves the IME inset unhandled; the count field needs it.
        Column(modifier = Modifier.imePadding()) {
            content(close)
        }
    }
}

@Composable
private fun AddressSearchStartContent(
    uiState: ZcashAddressSearchUiState,
    onMoreChange: (String) -> Unit,
    onStart: () -> Unit,
    onCheckAllAgain: () -> Unit,
    onClose: () -> Unit,
) {
    BottomSheetHeader(title = stringResource(Res.string.zec_address_search_title), onCloseClick = onClose) {
        subhead2_grey(
            text = stringResource(Res.string.zec_address_search_description),
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        VSpacer(height = 16.dp)
        Plate {
            PlateRow(height = 48.dp) {
                body_grey(text = stringResource(Res.string.zec_address_search_checked))
                Spacer(Modifier.weight(1f))
                BodyPrimary(listOfNotNull(checkedAddresses(uiState), uiState.checkedDate).joinToString(" · "))
            }
            HsDivider(thickness = 1.dp)
            PlateRow(height = 56.dp) {
                body_grey(text = stringResource(Res.string.zec_address_search_more))
                Spacer(Modifier.weight(1f))
                CountInput(value = uiState.moreText, onValueChange = onMoreChange)
            }
        }
        Column(modifier = Modifier.padding(horizontal = 32.dp)) {
            uiState.total?.let { total ->
                VSpacer(height = 8.dp)
                val totalText = uiState.totalText.orEmpty()
                subhead2_grey(text = pluralStringResource(Res.plurals.zec_address_search_total, total, totalText))
            }
            uiState.longSearchMinutes?.let { minutes ->
                VSpacer(height = 4.dp)
                subhead2_warning(text = stringResource(Res.string.zec_address_search_long, minutes))
            }
        }
        VSpacer(height = 20.dp)
        ButtonPrimaryYellow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            title = stringResource(Res.string.zec_address_search_start),
            onClick = onStart,
            enabled = uiState.total != null,
        )
        VSpacer(height = 4.dp)
        ButtonSecondaryTransparent(
            modifier = Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 24.dp),
            title = stringResource(Res.string.zec_address_search_check_all_again),
            onClick = onCheckAllAgain,
        )
        VSpacer(height = 32.dp)
    }
}

@Composable
private fun AddressSearchRunningContent(
    uiState: ZcashAddressSearchUiState,
    onStop: () -> Unit,
    onClose: () -> Unit,
) {
    BottomSheetHeader(title = stringResource(Res.string.zec_address_search_title), onCloseClick = onClose) {
        Plate(modifier = Modifier.padding(16.dp), spacing = 12.dp) {
            Row {
                BodyPrimary(
                    text = stringResource(
                        Res.string.zec_address_search_progress,
                        uiState.checkedText,
                        uiState.targetText,
                    ),
                    modifier = Modifier.weight(1f),
                )
                body_grey(text = "${uiState.percent}%")
            }
            ProgressBar(percent = uiState.percent)
            Row {
                subhead2_grey(
                    text = stringResource(Res.string.zec_address_search_found),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = uiState.foundText,
                    style = ComposeAppTheme.typography.subhead2,
                    color = ComposeAppTheme.colors.textPrimary,
                )
            }
        }
        VSpacer(height = 12.dp)
        subhead2_grey(
            text = stringResource(Res.string.zec_address_search_hint),
            modifier = Modifier.padding(horizontal = 32.dp),
        )
        VSpacer(height = 20.dp)
        ButtonPrimaryTransparent(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            title = stringResource(Res.string.zec_address_search_stop),
            textColor = ComposeAppTheme.colors.statusError,
            onClick = onStop,
        )
        VSpacer(height = 12.dp)
        ButtonPrimaryYellow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            title = stringResource(Res.string.Button_Close),
            onClick = onClose,
        )
        VSpacer(height = 32.dp)
    }
}

@Composable
private fun Plate(
    modifier: Modifier = Modifier,
    spacing: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ComposeAppTheme.colors.surfacePrimary)
            .then(modifier),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        content()
    }
}

@Composable
private fun PlateRow(height: Dp, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(height).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

@Composable
private fun BodyPrimary(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = ComposeAppTheme.typography.body,
        color = ComposeAppTheme.colors.textPrimary,
    )
}

@Composable
private fun CountInput(value: String, onValueChange: (String) -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = ColoredTextStyle(
            color = ComposeAppTheme.colors.textPrimary,
            textStyle = ComposeAppTheme.typography.body,
            textAlign = TextAlign.End,
        ),
        // Digits-only keypad: a "." or "," dropped by the filter would shift the cursor back.
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        cursorBrush = SolidColor(ComposeAppTheme.colors.brandDefault),
        modifier = Modifier
            .size(width = 110.dp, height = 36.dp)
            .clip(shape)
            .border(1.dp, ComposeAppTheme.colors.borderDefault, shape)
            .background(ComposeAppTheme.colors.backgroundBase),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier.fillMaxHeight().padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                innerTextField()
            }
        },
    )
}

@Composable
private fun ProgressBar(percent: Int) {
    val shape = RoundedCornerShape(3.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(shape)
            .background(ComposeAppTheme.colors.surfaceElevated),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(percent.coerceIn(0, 100) / 100f)
                .fillMaxHeight()
                .clip(shape)
                .background(ComposeAppTheme.colors.brandDefault),
        )
    }
}

private val PREVIEW_IDLE = ZcashAddressSearchUiState(
    running = false,
    checked = 520,
    checkedText = "520",
    checkedDate = "21.11",
    targetText = "520",
    percent = 100,
    foundText = "0",
    moreText = "500",
    total = 1_020,
    totalText = "1 020",
    longSearchMinutes = null,
)

@Preview
@Composable
private fun ZcashAddressSearchRowPreview() {
    ComposeAppTheme {
        Column(Modifier.width(390.dp)) {
            ZcashAddressSearchRow(PREVIEW_IDLE, {}, {}, {}, {})
            ZcashAddressSearchRow(PREVIEW_IDLE.copy(running = true, percent = 43), {}, {}, {}, {})
        }
    }
}

@Preview
@Composable
private fun AddressSearchStartContentPreview() {
    ComposeAppTheme {
        AddressSearchStartContent(PREVIEW_IDLE, {}, {}, {}, {})
    }
}

@Preview
@Composable
private fun AddressSearchStartContentLongSearchPreview() {
    ComposeAppTheme {
        AddressSearchStartContent(
            uiState = PREVIEW_IDLE.copy(moreText = "5000", total = 5_520, totalText = "5 520", longSearchMinutes = 4),
            onMoreChange = {},
            onStart = {},
            onCheckAllAgain = {},
            onClose = {},
        )
    }
}

@Preview
@Composable
private fun AddressSearchRunningContentPreview() {
    ComposeAppTheme {
        AddressSearchRunningContent(
            uiState = PREVIEW_IDLE.copy(running = true, checkedText = "2 360", targetText = "5 520", percent = 43),
            onStop = {},
            onClose = {},
        )
    }
}
