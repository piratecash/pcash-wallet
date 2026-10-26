package cash.p.terminal.modules.balance.token.addresspoisoning

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.transactions.TransactionCell
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.address_poisoning_view
import cash.p.terminal.resources.address_poisoning_view_compact
import cash.p.terminal.resources.address_poisoning_view_select_style
import cash.p.terminal.resources.address_poisoning_view_standard
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.HsCheckbox
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.subhead2_leah
import cash.p.terminal.ui_compose.entities.SectionItemPosition
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AddressPoisoningViewScreen(
    uiState: AddressPoisoningViewUiState,
    onSelect: (AddressPoisoningViewMode) -> Unit,
    onClose: () -> Unit,
) {
    Scaffold(
        topBar = {
            AppBar(
                title = stringResource(Res.string.address_poisoning_view),
                navigationIcon = { HsBackButton(onClick = onClose) },
            )
        },
        containerColor = ComposeAppTheme.colors.backgroundBase,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            VSpacer(23.dp)
            subhead2_grey(
                text = stringResource(Res.string.address_poisoning_view_select_style),
                modifier = Modifier.padding(horizontal = 26.dp),
            )
            VSpacer(20.dp)
            ModeOptionCard(
                label = stringResource(Res.string.address_poisoning_view_standard),
                selected = uiState.selectedMode == AddressPoisoningViewMode.STANDARD,
                onClick = { onSelect(AddressPoisoningViewMode.STANDARD) },
            ) {
                TransactionCell(
                    item = uiState.standardItem,
                    position = SectionItemPosition.Single,
                    onValueClick = { onSelect(AddressPoisoningViewMode.STANDARD) },
                    onClick = { onSelect(AddressPoisoningViewMode.STANDARD) },
                )
            }
            VSpacer(12.dp)
            ModeOptionCard(
                label = stringResource(Res.string.address_poisoning_view_compact),
                selected = uiState.selectedMode == AddressPoisoningViewMode.COMPACT,
                onClick = { onSelect(AddressPoisoningViewMode.COMPACT) },
            ) {
                TransactionCell(
                    item = uiState.compactItem,
                    position = SectionItemPosition.Single,
                    onValueClick = { onSelect(AddressPoisoningViewMode.COMPACT) },
                    onClick = { onSelect(AddressPoisoningViewMode.COMPACT) },
                )
            }
            VSpacer(32.dp)
        }
    }
}

@Composable
private fun ModeOptionCard(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    preview: @Composable () -> Unit,
) {
    val borderColor = if (selected) {
        ComposeAppTheme.colors.brandDefault
    } else {
        ComposeAppTheme.colors.borderDefault
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HsCheckbox(checked = selected, onCheckedChange = { onClick() })
            HSpacer(16.dp)
            subhead2_leah(text = label)
        }
        VSpacer(26.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, ComposeAppTheme.colors.borderDefault, RoundedCornerShape(12.dp))
        ) {
            preview()
        }
    }
}
