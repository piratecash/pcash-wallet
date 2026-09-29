package cash.p.terminal.ui_compose.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

data class AppSelectorItem<T>(
    val title: String,
    val selected: Boolean,
    val item: T,
    val subtitle: String? = null,
)

/** Calls [onSelect] for a row other than the selected one, then [onDismiss]. */
@Composable
fun <T> AppSelectorDialog(
    title: String?,
    items: List<AppSelectorItem<T>>,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AppDialog(onDismissRequest = onDismiss, modifier = Modifier.width(280.dp)) {
        title?.let { SelectorHeader(it) }
        Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            items.forEachIndexed { index, item ->
                if (index != 0 || title != null) HsDivider(thickness = 1.dp)
                SelectorRow(item) {
                    if (!item.selected) onSelect(item.item)
                    onDismiss()
                }
            }
        }
    }
}

@Composable
private fun SelectorHeader(title: String) {
    Box(modifier = Modifier.height(40.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
        subhead1_grey(
            text = title,
            modifier = Modifier.padding(horizontal = 16.dp),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun <T> SelectorRow(item: AppSelectorItem<T>, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = item.title,
            style = ComposeAppTheme.typography.body,
            color = if (item.selected) ComposeAppTheme.colors.brandDefault else ComposeAppTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        item.subtitle?.let {
            Text(
                text = it,
                modifier = Modifier.padding(top = 1.dp),
                style = ComposeAppTheme.typography.subhead2,
                color = ComposeAppTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}
