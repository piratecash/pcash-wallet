package cash.p.terminal.ui.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.core.Caution
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.ButtonsGroupWithShade
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.entities.DataState
import cash.p.terminal.ui_compose.entities.FormsInputStateWarning
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Composable
fun AddSourceScreen(
    title: String,
    onClose: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
    fields: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(windowInsets)
            .background(ComposeAppTheme.colors.backgroundBase)
    ) {
        AppBar(
            title = title,
            menuItems = listOf(
                MenuItem(
                    title = TranslatableString.ResString(R.string.Button_Close),
                    icon = R.drawable.ic_close_24,
                    onClick = onClose
                )
            )
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            fields()
            Spacer(Modifier.height(60.dp))
        }

        ButtonsGroupWithShade {
            ButtonPrimaryYellow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                title = stringResource(R.string.Button_Add),
                onClick = onAdd,
            )
        }
    }
}

fun Caution?.toInputState(): DataState.Error? = when (this?.type) {
    Caution.Type.Error -> DataState.Error(Exception(text))
    Caution.Type.Warning -> DataState.Error(FormsInputStateWarning(text))
    null -> null
}

@Preview
@Composable
private fun AddSourceScreenPreview() {
    ComposeAppTheme {
        AddSourceScreen(title = "Add source", onClose = {}, onAdd = {}) {
            FormsInput(
                modifier = Modifier.padding(horizontal = 16.dp),
                hint = "",
                onValueChange = {}
            )
        }
    }
}
