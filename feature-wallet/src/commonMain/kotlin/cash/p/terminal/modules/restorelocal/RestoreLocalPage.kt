package cash.p.terminal.modules.restorelocal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import cash.p.terminal.resources.Button_Close
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_close_24
import cash.p.terminal.ui_compose.components.AppModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cash.p.terminal.core.Caution
import cash.p.terminal.modules.backuplocal.fullbackup.OtherBackupItems
import cash.p.terminal.modules.contacts.screen.ConfirmationBottomSheet
import cash.p.terminal.modules.declinedtokens.DeclinedTokensSheets
import cash.p.terminal.feature.wallet.AppRestarter
import cash.p.terminal.ui_compose.components.ButtonsGroupWithShade
import cash.p.terminal.modules.restoreaccount.RestoreSelectCoinsPage
import cash.p.terminal.modules.restoreaccount.RestoreViewModel
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpFrom
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.navigation.viewModelStoreOwnerForPage
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellowWithSpinner
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.FormsInputPassword
import cash.p.terminal.ui_compose.components.HeaderText
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.InfoText
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.subhead2_lucian
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.reflect.KClass
import cash.p.terminal.resources.BackupManager_BackupFileContents
import cash.p.terminal.resources.BackupManager_BackupRequired
import cash.p.terminal.resources.BackupManager_BаckupFile
import cash.p.terminal.resources.BackupManager_MergeButton
import cash.p.terminal.resources.BackupManager_MergeDescription
import cash.p.terminal.resources.BackupManager_MergeTitle
import cash.p.terminal.resources.BackupManager_Restore
import cash.p.terminal.resources.BackupManager_Wallets
import cash.p.terminal.resources.Button_Cancel
import cash.p.terminal.resources.Button_Restore
import cash.p.terminal.resources.Hud_Text_Restored
import cash.p.terminal.resources.ImportBackupFile_BackupPassword
import cash.p.terminal.resources.ImportBackupFile_EnterPassword
import cash.p.terminal.resources.ImportBackupFile_EnterPassword_Description
import cash.p.terminal.resources.icon_add_to_wallet_2_24
import cash.p.terminal.resources.icon_warning_2_20
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource

class RestoreLocalPage(val input: Input) : HSPage(screenshotEnabled = false) {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val mainViewModel: RestoreViewModel = viewModel { RestoreViewModel() }
        val viewModel = koinViewModel<RestoreLocalViewModel> {
            parametersOf(input.backupFilePath, input.fileName)
        }
        RestoreLocalScreen(
            viewModel = viewModel,
            mainViewModel = mainViewModel,
            onBackClick = navigation::navigateUpSafely,
            close = { navigation.removeLastUntil(input.popOffOnSuccess, input.popOffInclusive) },
            openSelectCoins = {
                navigation.slideFromRight(
                    RestoreSelectCoinsPage(RestoreLocalPage::class, input.popOffOnSuccess, input.popOffInclusive)
                )
            },
            openBackupItems = {
                navigation.slideFromRight(BackupFileItemsPage(input.popOffOnSuccess, input.popOffInclusive))
            }
        )

        DeclinedTokensSheetsOnTop(navigation, this, viewModel)
    }

    data class Input(
        val popOffOnSuccess: KClass<out HSPage>,
        val popOffInclusive: Boolean,
        val backupFilePath: String,
        val fileName: String?
    )
}

class BackupFileItemsPage(
    val popOffOnSuccess: KClass<out HSPage>,
    val popOffInclusive: Boolean,
) : HSPage(screenshotEnabled = false) {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val appRestarter = koinInject<AppRestarter>()
        val viewModel = koinViewModel<RestoreLocalViewModel>(
            viewModelStoreOwner = navigation.viewModelStoreOwnerForPage(RestoreLocalPage::class)
        )
        BackupFileItems(
            viewModel,
            // Also called from a LaunchedEffect on a parse error.
            onBackClick = { navigation.navigateUpFrom(this) },
            close = { navigation.removeLastUntil(popOffOnSuccess, popOffInclusive) },
            reloadApp = appRestarter::restart
        )

        DeclinedTokensSheetsOnTop(navigation, this, viewModel)
    }
}

// Both pages can raise it; only the top one renders it, so a transition never shows it twice.
@Composable
private fun DeclinedTokensSheetsOnTop(navigation: HSNavigation, page: HSPage, viewModel: RestoreLocalViewModel) {
    if (navigation.lastOrNull() === page) {
        DeclinedTokensSheets(viewModel)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun RestoreLocalScreen(
    viewModel: RestoreLocalViewModel,
    mainViewModel: RestoreViewModel,
    onBackClick: () -> Unit,
    close: () -> Unit,
    openSelectCoins: () -> Unit,
    openBackupItems: () -> Unit
) {
    val uiState = viewModel.uiState
    val hudMessenger = rememberHudMessenger()
    val restoredText = stringResource(Res.string.Hud_Text_Restored)
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(uiState.restored) {
        if (uiState.restored) {
            hudMessenger.showSuccess(restoredText, Res.drawable.icon_add_to_wallet_2_24)
            delay(300)
            close.invoke()
        }
    }

    LaunchedEffect(uiState.parseError) {
        uiState.parseError?.let { error ->
            hudMessenger.showError(error.message ?: error::class.simpleName.orEmpty())
            onBackClick.invoke()
        }
    }

    LaunchedEffect(uiState.showSelectCoins) {
        uiState.showSelectCoins?.let { accountType ->
            mainViewModel.setAccountData(
                accountType,
                viewModel.accountName,
                uiState.manualBackup,
                true
            )
            keyboardController?.hide()
            delay(300)
            openSelectCoins.invoke()
            viewModel.onSelectCoinsShown()
        }
    }

    LaunchedEffect(uiState.showBackupItems) {
        if (uiState.showBackupItems) {
            keyboardController?.hide()
            delay(300)
            openBackupItems.invoke()
            viewModel.onBackupItemsShown()
        }
    }

    Scaffold(
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = {
            AppBar(
                title = stringResource(Res.string.ImportBackupFile_EnterPassword),
                menuItems = listOf(
                    MenuItem(
                        title = TranslatableString.ResString(Res.string.Button_Close),
                        icon = Res.drawable.ic_close_24,
                        onClick = onBackClick
                    )
                )
            )
        }
    ) {
        Column(modifier = Modifier.padding(it)) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(
                        rememberScrollState()
                    )
            ) {

                InfoText(text = stringResource(Res.string.ImportBackupFile_EnterPassword_Description))
                VSpacer(24.dp)
                FormsInputPassword(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    hint = stringResource(Res.string.ImportBackupFile_BackupPassword),
                    state = uiState.passphraseState,
                    onValueChange = viewModel::onChangePassphrase,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            keyboardController?.hide()
                            viewModel.onImportClick()
                        }
                    ),
                    enabled = uiState.showButtonSpinner.not(),
                )
                VSpacer(32.dp)
            }

            ButtonsGroupWithShade {
                ButtonPrimaryYellowWithSpinner(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp),
                    title = stringResource(Res.string.Button_Restore),
                    showSpinner = uiState.showButtonSpinner,
                    enabled = uiState.showButtonSpinner.not(),
                    onClick = viewModel::onImportClick
                )
            }
        }
    }

}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun BackupFileItems(
    viewModel: RestoreLocalViewModel,
    onBackClick: () -> Unit,
    close: () -> Unit,
    reloadApp: () -> Unit
) {
    val uiState = viewModel.uiState
    val walletBackupViewItems = viewModel.uiState.walletBackupViewItems
    val otherBackupViewItems = viewModel.uiState.otherBackupViewItems
    val hudMessenger = rememberHudMessenger()
    val restoredText = stringResource(Res.string.Hud_Text_Restored)

    LaunchedEffect(uiState.restored) {
        if (uiState.restored) {
            hudMessenger.showSuccess(restoredText, Res.drawable.icon_add_to_wallet_2_24)
            delay(300)
            close.invoke()
            reloadApp.invoke()
        }
    }

    LaunchedEffect(uiState.parseError) {
        uiState.parseError?.let { error ->
            hudMessenger.showError(error.message ?: error::class.simpleName.orEmpty())
            onBackClick.invoke()
        }
    }

    val bottomSheetState = rememberModalBottomSheetState(ModalBottomSheetValue.Hidden)
    val coroutineScope = rememberCoroutineScope()

    AppModalBottomSheetLayout(
        sheetState = bottomSheetState,
        sheetContent = {
            ConfirmationBottomSheet(
                title = stringResource(Res.string.BackupManager_MergeTitle),
                text = stringResource(Res.string.BackupManager_MergeDescription),
                iconPainter = painterResource(Res.drawable.icon_warning_2_20),
                iconTint = ColorFilter.tint(ComposeAppTheme.colors.statusError),
                confirmText = stringResource(Res.string.BackupManager_MergeButton),
                cautionType = Caution.Type.Error,
                cancelText = stringResource(Res.string.Button_Cancel),
                onConfirm = {
                    viewModel.restoreFullBackup()
                    coroutineScope.launch { bottomSheetState.hide() }
                },
                onClose = {
                    coroutineScope.launch { bottomSheetState.hide() }
                }
            )
        }
    ) {
        Scaffold(
            containerColor = ComposeAppTheme.colors.backgroundBase,
            topBar = {
                AppBar(
                    title = stringResource(Res.string.BackupManager_BаckupFile),
                    navigationIcon = {
                        HsBackButton(onClick = onBackClick)
                    },
                )
            },
            bottomBar = {
                ButtonsGroupWithShade(modifier = Modifier.navigationBarsPadding()) {
                    ButtonPrimaryYellow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp),
                        title = stringResource(Res.string.BackupManager_Restore),
                        onClick = {
                            if (viewModel.shouldShowReplaceWarning()) {
                                coroutineScope.launch { bottomSheetState.show() }
                            } else {
                                viewModel.restoreFullBackup()
                            }
                        }
                    )
                }
            }
        ) {
            LazyColumn(modifier = Modifier.padding(it)) {
                item {
                    InfoText(
                        text = stringResource(Res.string.BackupManager_BackupFileContents),
                        paddingBottom = 32.dp
                    )
                }

                if (walletBackupViewItems.isNotEmpty()) {
                    item {
                        HeaderText(text = stringResource(Res.string.BackupManager_Wallets))
                        CellUniversalLawrenceSection(
                            items = walletBackupViewItems,
                            showFrame = true
                        ) { walletBackupViewItem ->
                            RowUniversal(
                                modifier = Modifier.padding(horizontal = 16.dp),
                            ) {

                                Column(modifier = Modifier.weight(1f)) {
                                    body_leah(text = walletBackupViewItem.name)
                                    if (walletBackupViewItem.backupRequired) {
                                        subhead2_lucian(
                                            text = stringResource(Res.string.BackupManager_BackupRequired)
                                        )
                                    } else {
                                        subhead2_grey(
                                            text = walletBackupViewItem.type,
                                            overflow = TextOverflow.Ellipsis,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                        VSpacer(height = 24.dp)
                    }
                }

                item {
                    OtherBackupItems(otherBackupViewItems)
                    VSpacer(height = 32.dp)
                }
            }
        }
    }

}
