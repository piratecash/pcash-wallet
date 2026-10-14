package cash.p.terminal.ui_compose.components

import androidx.compose.runtime.Composable
import cash.p.terminal.strings.helpers.WithTranslatableTitle
import cash.p.terminal.ui_compose.Select
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun <T : WithTranslatableTitle> AlertGroup(
    title: StringResource,
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
