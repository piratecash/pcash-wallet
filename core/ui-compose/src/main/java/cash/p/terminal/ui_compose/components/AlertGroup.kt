package cash.p.terminal.ui_compose.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cash.p.terminal.strings.helpers.WithTranslatableTitle
import cash.p.terminal.ui_compose.Select

@Composable
fun <T : WithTranslatableTitle> AlertGroup(
    @StringRes title: Int,
    select: Select<T>,
    onSelect: (T) -> Unit,
    onDismiss: (() -> Unit)
) {
    AppSelectorDialog(
        title = stringResource(title),
        items = select.options.map { AppSelectorItem(it.title.getString(), it == select.selected, it) },
        onSelect = onSelect,
        onDismiss = onDismiss,
    )
}
