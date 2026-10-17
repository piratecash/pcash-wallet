package cash.p.terminal.shared.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.settings.security.autolock.AutoLockInterval
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Settings_AutoLock
import cash.p.terminal.resources.ic_checkmark_20
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun AutoLockIntervalsContent(
    selected: AutoLockInterval?,
    onSelect: (AutoLockInterval) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = {
            AppBar(
                title = stringResource(Res.string.Settings_AutoLock),
                navigationIcon = { HsBackButton(onClick = onBack) },
            )
        }
    ) {
        Column(modifier = Modifier.padding(it)) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                VSpacer(12.dp)
                CellUniversalLawrenceSection(AutoLockInterval.entries) { interval ->
                    IntervalCell(interval, interval == selected, onSelect)
                }
            }
        }
    }
}

@Composable
private fun IntervalCell(
    item: AutoLockInterval,
    checked: Boolean,
    onClick: (AutoLockInterval) -> Unit
) {
    RowUniversal(
        onClick = { onClick(item) }
    ) {
        HSpacer(16.dp)
        body_leah(
            modifier = Modifier.weight(1f),
            text = stringResource(item.title)
        )
        Box(
            modifier = Modifier
                .width(52.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(
                    painter = painterResource(Res.drawable.ic_checkmark_20),
                    tint = ComposeAppTheme.colors.brandDefault,
                    contentDescription = null,
                )
            }
        }
    }
}
