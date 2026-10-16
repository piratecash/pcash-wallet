package cash.p.terminal.modules.restoreaccount.restoremenu

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
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
import cash.p.terminal.ui_compose.components.B2
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.AppSelectorDialog
import cash.p.terminal.ui_compose.components.AppSelectorItem
import cash.p.terminal.ui_compose.components.subhead1_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Restore_By
import cash.p.terminal.resources.Restore_RestoreBy
import cash.p.terminal.resources.ic_down_arrow_20

@Composable
fun RestoreByMenu(
    viewModel: RestoreMenuViewModel
) {
    ByMenu(
        menuTitle = stringResource(Res.string.Restore_By),
        menuValue = stringResource(viewModel.restoreOption.titleRes),
        selectorDialogTitle = stringResource(Res.string.Restore_RestoreBy),
        selectorItems = viewModel.restoreOptions.map {
            AppSelectorItem(
                stringResource(it.titleRes),
                it == viewModel.restoreOption,
                it
            )
        },
        onSelectItem = {
            viewModel.onRestoreOptionSelected(it)
        }
    )
}

@Composable
fun <T> ByMenu(
    menuTitle: String,
    menuValue: String,
    selectorDialogTitle: String,
    selectorItems: List<AppSelectorItem<T>>,
    onSelectItem: (T) -> Unit
) {
    var showSelectorDialog by remember { mutableStateOf(false) }

    if (showSelectorDialog) {
        AppSelectorDialog(
            title = selectorDialogTitle,
            items = selectorItems,
            onDismiss = {
                showSelectorDialog = false
            },
            onSelect = onSelectItem
        )
    }

    CellUniversalLawrenceSection(
        listOf {
            RowUniversal(
                modifier = Modifier.padding(horizontal = 16.dp),
                onClick = { showSelectorDialog = true }
            ) {
                B2(text = menuTitle)
                Spacer(Modifier.weight(1f))
                Row {
                    subhead1_grey(text = menuValue)
                    Icon(
                        modifier = Modifier.padding(start = 4.dp),
                        painter = painterResource(Res.drawable.ic_down_arrow_20),
                        contentDescription = null,
                        tint = ComposeAppTheme.colors.iconSecondary
                    )
                }
            }
        })
}
