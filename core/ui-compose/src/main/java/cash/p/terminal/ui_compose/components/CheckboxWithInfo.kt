@file:Suppress("PackageNaming")

package cash.p.terminal.ui_compose.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.R
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Composable
fun CheckboxWithInfo(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onInfoClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckboxLabel(title, checked, onCheckedChange, modifier = Modifier.weight(1f, fill = false))
        HsIconButton(
            onClick = onInfoClick,
            modifier = Modifier.heightIn(min = 48.dp),
            minWidth = 36.dp,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_info_20),
                contentDescription = stringResource(R.string.Info_Title),
                modifier = Modifier.size(20.dp),
                tint = ComposeAppTheme.colors.grey,
            )
        }
    }
}

@Composable
private fun CheckboxLabel(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HsCheckbox(
            checked = checked,
            modifier = Modifier.clearAndSetSemantics { },
            onCheckedChange = onCheckedChange,
        )
        subhead2_leah(
            text = title,
            modifier = Modifier.padding(start = 16.dp).weight(1f, fill = false),
        )
    }
}
