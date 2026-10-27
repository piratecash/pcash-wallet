package cash.p.terminal.modules.backuplocal.fullbackup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.Icon
import cash.p.terminal.ui_compose.components.AppModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material3.Scaffold
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import cash.p.terminal.core.Caution
import cash.p.terminal.core.rememberTermsAcceptedAction
import cash.p.terminal.core.validateAndSaveBackup
import cash.p.terminal.feature.wallet.BackupFileLauncher
import cash.p.terminal.feature.wallet.rememberSecretAction
import cash.p.terminal.modules.contacts.screen.ConfirmationBottomSheet
import cash.p.terminal.modules.backuplocal.BackupLocalPage
import cash.p.terminal.modules.restorelocal.RestoreLocalPage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.body_brand
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import cash.p.terminal.resources.BackupManager_CreateBackup
import cash.p.terminal.resources.BackupManager_RestoreBackup
import cash.p.terminal.resources.BackupManager_Title
import cash.p.terminal.resources.Button_Cancel
import cash.p.terminal.resources.ImportWallet_SelectAnotherFile
import cash.p.terminal.resources.ImportWallet_WarningInvalidJson
import cash.p.terminal.resources.ImportWallet_WarningInvalidJsonDescription
import cash.p.terminal.resources.ic_download_20
import cash.p.terminal.resources.ic_plus
import cash.p.terminal.resources.icon_warning_2_20
import cash.p.terminal.resources.Res
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource

class BackupManagerPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val openAfterTerms = navigation.rememberTermsAcceptedAction<HSPage>(navigation::slideFromBottom)
        val createBackup = navigation.rememberSecretAction { navigation.slideFromRight(BackupLocalPage(null)) }
        BackupManagerScreen(
            onBackClick = {
                navigation.navigateUpSafely()
            },
            onRestoreBackup = { backupFilePath, fileName ->
                openAfterTerms(
                    RestoreLocalPage(
                        RestoreLocalPage.Input(
                            BackupManagerPage::class,
                            false,
                            backupFilePath,
                            fileName,
                        )
                    )
                )
            },
            onCreateBackup = createBackup
        )
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun BackupManagerScreen(
    onBackClick: () -> Unit,
    onRestoreBackup: (backupFilePath: String, fileName: String?) -> Unit,
    onCreateBackup: () -> Unit,
) {
    val bottomSheetState = rememberModalBottomSheetState(ModalBottomSheetValue.Hidden)
    val coroutineScope = rememberCoroutineScope()
    val dispatcherProvider = koinInject<DispatcherProvider>()

    val openRestorePicker = koinInject<BackupFileLauncher>().rememberOpenBackupFile { source, fileName ->
        coroutineScope.launch {
            try {
                val backupFilePath = withContext(dispatcherProvider.io) {
                    validateAndSaveBackup(checkNotNull(source.read()) { "Cannot open input stream" })
                }
                onRestoreBackup(backupFilePath, fileName)
            } catch (_: Throwable) {
                delay(300)
                bottomSheetState.show()
            }
        }
    }

    AppModalBottomSheetLayout(
        sheetState = bottomSheetState,
        sheetContent = {
            ConfirmationBottomSheet(
                title = stringResource(Res.string.ImportWallet_WarningInvalidJson),
                text = stringResource(Res.string.ImportWallet_WarningInvalidJsonDescription),
                iconPainter = painterResource(Res.drawable.icon_warning_2_20),
                iconTint = ColorFilter.tint(ComposeAppTheme.colors.statusError),
                confirmText = stringResource(Res.string.ImportWallet_SelectAnotherFile),
                cautionType = Caution.Type.Warning,
                cancelText = stringResource(Res.string.Button_Cancel),
                onConfirm = {
                    openRestorePicker()
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
                    title = stringResource(Res.string.BackupManager_Title),
                    navigationIcon = {
                        HsBackButton(onClick = onBackClick)
                    },
                )
            }
        ) {
            Column(modifier = Modifier.padding(it)) {
                Spacer(modifier = Modifier.height(12.dp))
                CellUniversalLawrenceSection(
                    buildList {
                        add {
                            RowUniversal(
                                onClick = openRestorePicker
                            ) {
                                Icon(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    painter = painterResource(Res.drawable.ic_download_20),
                                    contentDescription = null,
                                    tint = ComposeAppTheme.colors.brandDefault
                                )
                                body_brand(text = stringResource(Res.string.BackupManager_RestoreBackup))
                            }
                        }

                        add {
                            RowUniversal(onClick = onCreateBackup) {
                                Icon(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    painter = painterResource(Res.drawable.ic_plus),
                                    contentDescription = null,
                                    tint = ComposeAppTheme.colors.brandDefault
                                )
                                body_brand(text = stringResource(Res.string.BackupManager_CreateBackup))
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
