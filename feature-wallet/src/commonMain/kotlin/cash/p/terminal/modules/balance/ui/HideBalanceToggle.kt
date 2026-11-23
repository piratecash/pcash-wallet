package cash.p.terminal.modules.balance.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.hide_balance
import cash.p.terminal.resources.ic_eye_20
import cash.p.terminal.resources.ic_eye_off
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun HideBalanceToggle(
    hidden: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onToggle
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        subhead2_grey(text = stringResource(Res.string.hide_balance))
        HSpacer(8.dp)
        Icon(
            painter = painterResource(if (hidden) Res.drawable.ic_eye_off else Res.drawable.ic_eye_20),
            contentDescription = null,
            tint = ComposeAppTheme.colors.iconSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Preview
@Composable
private fun HideBalanceTogglePreview() {
    ComposeAppTheme {
        Column {
            HideBalanceToggle(hidden = false, onToggle = {}, modifier = Modifier.padding(16.dp))
            HideBalanceToggle(hidden = true, onToggle = {}, modifier = Modifier.padding(16.dp))
        }
    }
}
