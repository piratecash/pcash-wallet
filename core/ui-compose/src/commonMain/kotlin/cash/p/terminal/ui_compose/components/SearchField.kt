package cash.p.terminal.ui_compose.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.resources.Button_Cancel
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_close_24
import cash.p.terminal.resources.ic_search
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun SearchField(
    onSearchTextChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
) {
    var showClearButton by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(plateBackground())
            .plateOutline(RoundedCornerShape(8.dp))
            .height(40.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Icon(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .size(24.dp),
            painter = painterResource(Res.drawable.ic_search),
            contentDescription = "search",
            tint = ComposeAppTheme.colors.brandDefault
        )
        BasicTextField(
            value = searchText,
            onValueChange = {
                searchText = it
                onSearchTextChanged.invoke(it)
                showClearButton = it.isNotEmpty()
            },
            textStyle = ComposeAppTheme.typography.body.copy(
                color = ComposeAppTheme.colors.textPrimary,
            ),
            maxLines = 1,
            singleLine = true,
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                keyboardController?.hide()
            }),
            cursorBrush = SolidColor(ComposeAppTheme.colors.brandDefault),
            modifier = Modifier
                .padding(end = 16.dp)
                .focusRequester(focusRequester)
                .weight(1f)
        )
        AnimatedVisibility(
            visible = showClearButton,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            HsIconButton(onClick = {
                searchText = ""
                onSearchTextChanged.invoke("")
                showClearButton = false
            }) {
                Icon(
                    painter = painterResource(Res.drawable.ic_close_24),
                    contentDescription = stringResource(Res.string.Button_Cancel),
                    tint = ComposeAppTheme.colors.iconSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
