package cash.p.terminal.ui.compose.components

import cash.p.terminal.resources.Button_Back
import cash.p.terminal.resources.Button_Cancel
import cash.p.terminal.resources.Button_Search
import cash.p.terminal.resources.ic_back
import cash.p.terminal.resources.ic_close_24
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_search
import cash.p.terminal.ui_compose.components.AppBarMenuButton
import cash.p.terminal.ui_compose.components.HsIconButton
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.components.body_disabled
import cash.p.terminal.ui_compose.components.title3_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@OptIn(ExperimentalMaterial3Api::class)
@ExperimentalAnimationApi
@Composable
fun SearchBar(
    title: String,
    searchHintText: String = "",
    searchOnlyMode: Boolean = false,
    searchModeInitial: Boolean = false,
    focusRequester: FocusRequester = remember { FocusRequester() },
    menuItems: List<MenuItem> = listOf(),
    onClose: () -> Unit,
    onSearchTextChanged: (String) -> Unit = {},
) {

    var searchMode by rememberSaveable { mutableStateOf(searchModeInitial) }
    val keyboardController = LocalSoftwareKeyboardController.current
    var searchText by rememberSaveable { mutableStateOf("") }
    val showClearButton = searchText.isNotEmpty()
    val reportSearchText by rememberUpdatedState(onSearchTextChanged)

    LaunchedEffect(Unit) {
        // A restored query: the caller's filter may have started over, so it hears the query again.
        if (searchText.isNotEmpty()) reportSearchText(searchText)
    }

    TopAppBar(
        title = {
            if (searchMode) {
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .focusRequester(focusRequester),
                    value = searchText,
                    onValueChange = {
                        searchText = it
                        onSearchTextChanged.invoke(it)
                    },
                    placeholder = {
                        body_disabled(
                            text = searchHintText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    textStyle = ComposeAppTheme.typography.body,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        cursorColor = ComposeAppTheme.colors.brandDefault,
                        focusedTextColor = ComposeAppTheme.colors.textPrimary,
                        unfocusedTextColor = ComposeAppTheme.colors.textPrimary
                    ),
                    maxLines = 1,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        keyboardController?.hide()
                    }),
                    trailingIcon = {
                        AnimatedVisibility(
                            visible = showClearButton,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            HsIconButton(onClick = {
                                searchText = ""
                                onSearchTextChanged.invoke("")
                            }) {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_close_24),
                                    contentDescription = stringResource(Res.string.Button_Cancel),
                                    tint = ComposeAppTheme.colors.brandDefault
                                )
                            }

                        }
                    },
                )

                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }
            } else {
                title3_leah(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = ComposeAppTheme.colors.backgroundBase),
        navigationIcon = {
            HsIconButton(onClick = {
                if (searchMode && !searchOnlyMode) {
                    searchText = ""
                    onSearchTextChanged.invoke("")
                    searchMode = false
                } else {
                    onClose.invoke()
                }
            }) {
                Icon(
                    painter = painterResource(Res.drawable.ic_back),
                    contentDescription = stringResource(Res.string.Button_Back),
                    tint = ComposeAppTheme.colors.brandDefault
                )
            }
        },
        actions = {
            if (!searchMode) {
                AppBarMenuButton(
                    icon = Res.drawable.ic_search,
                    tint = ComposeAppTheme.colors.brandDefault,
                    onClick = { searchMode = true },
                    description = stringResource(Res.string.Button_Search),
                )

                menuItems.forEach { menuItem ->
                    if (menuItem.icon != null) {
                        AppBarMenuButton(
                            icon = menuItem.icon!!,
                            onClick = menuItem.onClick,
                            description = menuItem.title.getString(),
                            enabled = menuItem.enabled,
                            tint = menuItem.tint
                        )
                    } else {
                        Text(
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .clickable(
                                    enabled = menuItem.enabled,
                                    onClick = menuItem.onClick
                                ),
                            text = menuItem.title.getString(),
                            style = ComposeAppTheme.typography.headline2,
                            color = if (menuItem.enabled) {
                                ComposeAppTheme.colors.brandDefault
                            } else {
                                ComposeAppTheme.colors.statusWarning50
                            }
                        )
                    }
                }
            }
        })

}
