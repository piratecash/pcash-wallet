package cash.p.terminal.modules.hodler

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.components.ButtonSecondaryWithIcon
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.AppSelectorDialog
import cash.p.terminal.ui_compose.components.AppSelectorItem
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.subhead1_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.hodler.LockTimeInterval
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Send_DialogLockTime
import cash.p.terminal.resources.Send_DialogSpeed
import cash.p.terminal.resources.Send_LockTime_HalfYear
import cash.p.terminal.resources.Send_LockTime_Hour
import cash.p.terminal.resources.Send_LockTime_Month
import cash.p.terminal.resources.Send_LockTime_Off
import cash.p.terminal.resources.Send_LockTime_Year
import cash.p.terminal.resources.ic_down_arrow_20
import cash.p.terminal.resources.ic_lock_20
import org.jetbrains.compose.resources.StringResource

@Composable
fun HSHodlerInput(
    lockTimeIntervals: List<LockTimeInterval?>,
    lockTimeInterval: LockTimeInterval?,
    onSelect: (LockTimeInterval?) -> Unit
) {
    var showSelectorDialog by remember { mutableStateOf(false) }
    if (showSelectorDialog) {
        AppSelectorDialog(
            title = stringResource(Res.string.Send_DialogSpeed),
            items = lockTimeIntervals.map {
                AppSelectorItem(stringResource(it.stringResId()), it == lockTimeInterval, it)
            },
            onDismiss = {
                showSelectorDialog = false
            },
            onSelect = {
                onSelect.invoke(it)
            }
        )
    }

    RowUniversal(
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = { showSelectorDialog = true }
        ),
    ) {
        body_leah(
            modifier = Modifier.padding(horizontal = 16.dp),
            text = stringResource(Res.string.Send_DialogLockTime),
        )
        Spacer(modifier = Modifier.weight(1f))
        ButtonSecondaryWithIcon(
            modifier = Modifier.height(28.dp),
            onClick = { showSelectorDialog = true },
            title = stringResource(lockTimeInterval.stringResId()),
            iconRight = painterResource(Res.drawable.ic_down_arrow_20),
        )
        Spacer(modifier = Modifier.width(16.dp))
    }
}

@Composable
fun HSHodler(
    lockTimeInterval: LockTimeInterval,
) {
    RowUniversal {
        Icon(
            modifier = Modifier.padding(horizontal = 16.dp),
            painter = painterResource(Res.drawable.ic_lock_20),
            tint = ComposeAppTheme.colors.iconSecondary,
            contentDescription = "lock icon",
        )
        subhead2_grey(
            modifier = Modifier.padding(end = 16.dp),
            text = stringResource(Res.string.Send_DialogLockTime),
        )
        Spacer(modifier = Modifier.weight(1f))
        subhead1_leah(text = stringResource(lockTimeInterval.stringResId()))
        Spacer(modifier = Modifier.width(16.dp))
    }
}

private fun LockTimeInterval?.stringResId(): StringResource {
    return when (this) {
        LockTimeInterval.hour -> Res.string.Send_LockTime_Hour
        LockTimeInterval.month -> Res.string.Send_LockTime_Month
        LockTimeInterval.halfYear -> Res.string.Send_LockTime_HalfYear
        LockTimeInterval.year -> Res.string.Send_LockTime_Year
        null -> Res.string.Send_LockTime_Off
    }
}
