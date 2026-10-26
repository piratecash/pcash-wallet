package cash.p.terminal.modules.transactions

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.components.HsSwitch
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.alpha_aml_title
import cash.p.terminal.resources.ic_info_20
import cash.p.terminal.resources.ic_star_filled_20

@Composable
fun AmlCheckRow(
    enabled: Boolean,
    onToggleChange: (Boolean) -> Unit,
    onInfoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, ComposeAppTheme.colors.statusWarning, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_star_filled_20),
            contentDescription = null,
            tint = ComposeAppTheme.colors.statusWarning,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = stringResource(Res.string.alpha_aml_title),
            color = ComposeAppTheme.colors.textPrimary,
            style = ComposeAppTheme.typography.body
        )
        Icon(
            painter = painterResource(Res.drawable.ic_info_20),
            contentDescription = null,
            tint = ComposeAppTheme.colors.iconSecondary,
            modifier = Modifier
                .size(20.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false, radius = 20.dp),
                    onClick = onInfoClick
                )
        )
        Spacer(Modifier.weight(1f))
        HsSwitch(
            checked = enabled,
            onCheckedChange = onToggleChange
        )
    }
}
