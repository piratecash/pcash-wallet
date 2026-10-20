package cash.p.terminal.modules.manageaccount

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import cash.p.terminal.resources.Info_Title
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_info_24
import cash.p.terminal.ui_compose.components.AppModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.feature.wallet.FaqTopic
import cash.p.terminal.feature.wallet.openFaq
import cash.p.terminal.modules.manageaccount.ui.ActionButton
import cash.p.terminal.modules.manageaccount.ui.ConfirmCopyBottomSheet
import cash.p.terminal.modules.manageaccount.ui.HidableContent
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.modules.manageaccount.ui.rememberCopyWithHud
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.components.TextImportantWarning
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlinx.coroutines.launch
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.resources.Alert_Copy
import cash.p.terminal.resources.PrivateKeys_NeverShareWarning

@Composable
fun SecretKeyScreen(
    navigation: HSNavigation,
    secretKey: String,
    title: String,
    hideScreenText: String,
) {
    val copyWithHud = rememberCopyWithHud()
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(
        initialValue = ModalBottomSheetValue.Hidden,
    )

    AppModalBottomSheetLayout(
        sheetState = sheetState,
        sheetContent = {
            ConfirmCopyBottomSheet(
                onConfirm = {
                    coroutineScope.launch {
                        copyWithHud(secretKey)
                        sheetState.hide()
                    }
                },
                onCancel = {
                    coroutineScope.launch {
                        sheetState.hide()
                    }
                }
            )
        }
    ) {
        Scaffold(
            containerColor = ComposeAppTheme.colors.backgroundBase,
            topBar = {
                AppBar(
                    title = title,
                    navigationIcon = {
                        HsBackButton(onClick = navigation::navigateUpSafely)
                    },
                    menuItems = listOf(
                        MenuItem(
                            title = TranslatableString.ResString(Res.string.Info_Title),
                            icon = Res.drawable.ic_info_24,
                            onClick = {
                                navigation.openFaq(FaqTopic.PrivateKeys)
                            }
                        )
                    )
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier.padding(paddingValues),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Top
                ) {
                    VSpacer(12.dp)
                    TextImportantWarning(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        text = stringResource(Res.string.PrivateKeys_NeverShareWarning)
                    )
                    VSpacer(24.dp)
                    HidableContent(secretKey, hideScreenText)
                }

                ActionButton(Res.string.Alert_Copy) {
                    coroutineScope.launch {
                        sheetState.show()
                    }
                }
            }
        }
    }
}
