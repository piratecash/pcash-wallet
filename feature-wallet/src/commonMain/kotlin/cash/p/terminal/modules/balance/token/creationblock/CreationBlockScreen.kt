package cash.p.terminal.modules.balance.token.creationblock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Restore_BirthdayHeight
import cash.p.terminal.resources.birthday_height_date_label
import cash.p.terminal.resources.birthday_height_description
import cash.p.terminal.resources.birthday_height_rescan
import cash.p.terminal.resources.restoreheight_hint_block_only
import cash.p.terminal.ui.compose.components.RestoreHeightInput
import cash.p.terminal.ui.compose.components.SelectDateBottomSheet
import cash.p.terminal.ui.compose.components.restoreGenesisDateMillis
import cash.p.terminal.ui.compose.components.restoreMaxDateMillis
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.ButtonsGroupWithShade
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.InfoText
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.subhead1_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import java.time.LocalDate
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun CreationBlockScreen(
    uiState: CreationBlockUiState,
    onHeightChange: (String) -> Unit,
    onDatePick: (LocalDate) -> Unit,
    onRescanClick: () -> Unit,
    onClose: () -> Unit,
    onRescanStart: () -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val currentOnRescanStart by rememberUpdatedState(onRescanStart)

    LaunchedEffect(uiState.rescanStarted) {
        if (uiState.rescanStarted) {
            currentOnRescanStart()
        }
    }

    Scaffold(
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = {
            AppBar(
                title = stringResource(Res.string.Restore_BirthdayHeight),
                navigationIcon = { HsBackButton(onClick = onClose) }
            )
        }
    ) { paddingValues ->
        CreationBlockContent(
            modifier = Modifier.padding(paddingValues),
            uiState = uiState,
            onHeightChange = onHeightChange,
            onCalendarClick = { showDatePicker = true },
            onRescanClick = onRescanClick,
        )
    }

    if (showDatePicker) {
        SelectDateBottomSheet(
            initialDateMillis = null,
            minDateMillis = restoreGenesisDateMillis(uiState.blockchainType),
            maxDateMillis = restoreMaxDateMillis(),
            onDateSelect = onDatePick,
            onDismiss = { showDatePicker = false },
        )
    }
}

@Composable
private fun CreationBlockContent(
    uiState: CreationBlockUiState,
    onHeightChange: (String) -> Unit,
    onCalendarClick: () -> Unit,
    onRescanClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            VSpacer(12.dp)
            InfoText(text = stringResource(Res.string.birthday_height_description))
            VSpacer(12.dp)
            RestoreHeightInput(
                modifier = Modifier.padding(horizontal = 16.dp),
                initial = uiState.heightText,
                hint = stringResource(Res.string.restoreheight_hint_block_only),
                error = uiState.error,
                pasteEnabled = true,
                onValueChange = onHeightChange,
                onCalendarClick = onCalendarClick,
                numericOnly = true,
            )
            VSpacer(12.dp)
            BlockDateRow(dateText = uiState.blockDateText)
        }
        ButtonsGroupWithShade {
            ButtonPrimaryYellow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                title = stringResource(Res.string.birthday_height_rescan),
                enabled = uiState.changed && !uiState.loading,
                loadingIndicator = uiState.loading,
                onClick = onRescanClick,
            )
        }
    }
}

@Composable
private fun BlockDateRow(dateText: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 0.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        subhead2_grey(text = stringResource(Res.string.birthday_height_date_label))
        subhead1_leah(text = dateText ?: "—")
    }
}
