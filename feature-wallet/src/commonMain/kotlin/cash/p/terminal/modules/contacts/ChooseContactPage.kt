package cash.p.terminal.modules.contacts

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.resources.Button_Search
import cash.p.terminal.resources.Contacts
import cash.p.terminal.resources.EmptyResults
import cash.p.terminal.resources.Market_Search_Hint
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_not_found
import cash.p.terminal.resources.icon_search
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.strings.helpers.shorten
import cash.p.terminal.ui.compose.components.ListEmptyView
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_disabled
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.title3_leah
import cash.p.terminal.ui_compose.theme.ColoredTextStyle
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.core.common.CommonParcelable
import io.horizontalsystems.core.common.CommonParcelize
import io.horizontalsystems.core.entities.BlockchainType
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class ChooseContactPage(val input: BlockchainType?) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        ChooseContactScreen(
            page = this,
            blockchainType = input,
            navigation = navigation
        )
    }

    @CommonParcelize
    data class Result(val address: String) : CommonParcelable
}

@Composable
private fun ChooseContactScreen(
    page: ChooseContactPage,
    blockchainType: BlockchainType?,
    navigation: HSNavigation
) {
    val blockchainTypeNonNull = blockchainType ?: return
    val viewModel = koinViewModel<ChooseContactViewModel> { parametersOf(blockchainTypeNonNull) }

    val items = viewModel.items

    Scaffold(
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = {
            var searchMode by remember { mutableStateOf(false) }

            AppBar(
                title = {
                    if (searchMode) {
                        var searchText by rememberSaveable { mutableStateOf("") }
                        val focusRequester = remember { FocusRequester() }

                        BasicTextField(
                            modifier = Modifier
                                .focusRequester(focusRequester),
                            value = searchText,
                            onValueChange = { value ->
                                searchText = value
                                viewModel.onEnterQuery(value)
                            },
                            singleLine = true,
                            textStyle = ColoredTextStyle(
                                color = ComposeAppTheme.colors.textPrimary,
                                textStyle = ComposeAppTheme.typography.body
                            ),
                            decorationBox = { innerTextField ->
                                if (searchText.isEmpty()) {
                                    body_disabled(stringResource(Res.string.Market_Search_Hint))
                                }
                                innerTextField()
                            },
                            cursorBrush = SolidColor(ComposeAppTheme.colors.brandDefault),
                        )
                        SideEffect {
                            focusRequester.requestFocus()
                        }
                    } else {
                        title3_leah(text = stringResource(Res.string.Contacts))
                    }
                },
                navigationIcon = {
                    HsBackButton(onClick = {
                        if (searchMode) {
                            viewModel.onEnterQuery(null)
                            searchMode = false
                        } else {
                            navigation.navigateUpSafely()
                        }
                    })
                },
                menuItems = if (searchMode) {
                    listOf()
                } else {
                    listOf(
                        MenuItem(
                            title = TranslatableString.ResString(Res.string.Button_Search),
                            icon = Res.drawable.icon_search,
                            onClick = {
                                searchMode = true
                            }
                        )
                    )
                }
            )
        }
    ) {
        Column(modifier = Modifier.padding(it)) {
            Crossfade(items.isEmpty(), label = "") { empty ->
                if (empty) {
                    ListEmptyView(
                        text = stringResource(Res.string.EmptyResults),
                        icon = Res.drawable.ic_not_found
                    )
                } else {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        VSpacer(height = 12.dp)
                        CellUniversalLawrenceSection(items, showFrame = true) { contact ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        navigation.setResult(
                                            page,
                                            ChooseContactPage.Result(contact.address)
                                        )
                                        navigation.navigateUpSafely()
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                body_leah(text = contact.name)
                                VSpacer(height = 1.dp)
                                subhead2_grey(text = contact.address.shorten())
                            }
                        }
                    }
                }
            }
        }
    }
}
