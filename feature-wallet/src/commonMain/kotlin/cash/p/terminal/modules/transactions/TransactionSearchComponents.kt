package cash.p.terminal.modules.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui.compose.components.ListEmptyView
import cash.p.terminal.ui_compose.components.HSCircularProgressIndicator
import cash.p.terminal.ui_compose.components.HsIconButton
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Balance_SearchingTransactions
import cash.p.terminal.resources.Button_Cancel
import cash.p.terminal.resources.Market_Search_Hint
import cash.p.terminal.resources.ic_close_24
import cash.p.terminal.resources.ic_not_found
import cash.p.terminal.resources.transactions_empty_search_results

// The hoisted [query] is a plain String fed from the ViewModel (emitState). The
// String overload of BasicTextField does NOT let us own the caret/selection or the
// IME composing region: when the external value echoes back a frame later (or the
// field is recreated inside the asset screen's sticky header), a fresh TextFieldValue
// defaults selection to TextRange.Zero and the caret snaps to the START of the text.
// Gboard tolerates this, but composing-region keyboards (Samsung Keyboard, Xiaomi/Redmi
// stock, Yandex) push every subsequent character to the beginning ("0x777" -> "7770x").
// Fix: keep the String API but own a TextFieldValue internally, syncing the external
// query only when it actually changes so we never clobber the caret while typing.
@Composable
fun TransactionSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    var fieldValueState by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(
            TextFieldValue(text = query, selection = TextRange(query.length))
        )
    }
    // Track the last value we pushed out and the last external value we saw, so we can
    // tell a genuine external change apart from our own echo coming back via emitState.
    var lastExternalQuery by rememberSaveable { mutableStateOf(query) }
    var lastSentQuery by rememberSaveable { mutableStateOf(query) }

    val externalQueryChanged = query != lastExternalQuery
    val fieldValue = if (externalQueryChanged && query != fieldValueState.text) {
        // A real external change (e.g. programmatic clear) — reset text and park the
        // caret at the end. Our own echo is filtered out by the query == text check.
        TextFieldValue(text = query, selection = TextRange(query.length))
    } else {
        fieldValueState
    }

    SideEffect {
        if (externalQueryChanged) {
            lastExternalQuery = query
            lastSentQuery = query
        }
        if (fieldValueState != fieldValue) {
            fieldValueState = fieldValue
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (fieldValue.text.isEmpty()) {
                Text(
                    text = stringResource(Res.string.Market_Search_Hint),
                    style = ComposeAppTheme.typography.body,
                    color = ComposeAppTheme.colors.textDisabled,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = fieldValue,
                onValueChange = { newValue ->
                    fieldValueState = newValue
                    if (newValue.text != lastSentQuery) {
                        lastSentQuery = newValue.text
                        onQueryChange(newValue.text)
                    }
                },
                textStyle = ComposeAppTheme.typography.body.copy(
                    color = ComposeAppTheme.colors.textPrimary,
                ),
                maxLines = 1,
                singleLine = true,
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        keyboardController?.hide()
                    }
                ),
                cursorBrush = SolidColor(ComposeAppTheme.colors.brandDefault),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
        }

        if (fieldValue.text.isNotEmpty()) {
            SearchCloseButton(
                onClick = {
                    fieldValueState = TextFieldValue(text = "", selection = TextRange.Zero)
                    lastSentQuery = ""
                    onQueryChange("")
                }
            )
        }
    }
}

@Composable
private fun SearchCloseButton(onClick: () -> Unit) {
    HsIconButton(onClick = onClick) {
        Icon(
            painter = painterResource(Res.drawable.ic_close_24),
            contentDescription = stringResource(Res.string.Button_Cancel),
            tint = ComposeAppTheme.colors.brandDefault,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
fun SearchEmptyResultsView() {
    ListEmptyView(
        text = stringResource(Res.string.transactions_empty_search_results),
        icon = Res.drawable.ic_not_found
    )
}


@Composable
fun SearchInProgressView(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HSCircularProgressIndicator()
        VSpacer(16.dp)
        subhead2_grey(
            text = stringResource(Res.string.Balance_SearchingTransactions),
            textAlign = TextAlign.Center,
        )
    }
}

