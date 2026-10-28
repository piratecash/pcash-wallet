package cash.p.terminal.modules.importwallet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import cash.p.terminal.ui_compose.components.AppModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material3.Scaffold
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import cash.p.terminal.core.Caution
import cash.p.terminal.core.rememberTermsAcceptedAction
import cash.p.terminal.feature.wallet.BackupFileLauncher
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.navigation.rememberQrScannerLauncher
import cash.p.terminal.modules.contacts.screen.ConfirmationBottomSheet
import cash.p.terminal.modules.manageaccounts.ManageAccountsModule
import cash.p.terminal.modules.restoreaccount.RestoreAccountPage
import cash.p.terminal.modules.restorelocal.RestoreLocalPage
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.headline2_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.jetbrains.compose.resources.DrawableResource
import kotlin.reflect.KClass
import cash.p.terminal.resources.Button_Cancel
import cash.p.terminal.resources.ImportWallet_BackupFile
import cash.p.terminal.resources.ImportWallet_BackupFile_Description
import cash.p.terminal.resources.ImportWallet_RecoveryPhrase
import cash.p.terminal.resources.ImportWallet_RecoveryPhrase_Description
import cash.p.terminal.resources.ImportWallet_SelectAnotherFile
import cash.p.terminal.resources.ImportWallet_WarningInvalidJson
import cash.p.terminal.resources.ImportWallet_WarningInvalidJsonDescription
import cash.p.terminal.resources.ManageAccounts_ImportWallet
import cash.p.terminal.resources.ic_download_24
import cash.p.terminal.resources.ic_edit_24
import cash.p.terminal.resources.ic_qr_scan_24
import cash.p.terminal.resources.icon_warning_2_20
import cash.p.terminal.resources.import_wallet_from_qr
import cash.p.terminal.resources.import_wallet_from_qr_description
import cash.p.terminal.resources.Res
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource


class ImportWalletPage(val input: ManageAccountsModule.Input?) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val popUpToInclusiveId = input?.popOffOnSuccess ?: ImportWalletPage::class
        val inclusive = input?.popOffInclusive ?: true

        ImportWalletScreen(navigation, popUpToInclusiveId, inclusive)
    }

}

@Composable
private fun ImportWalletScreen(
    navigation: HSNavigation,
    popUpToInclusiveId: KClass<out HSPage>,
    inclusive: Boolean
) {
    val bottomSheetState = rememberModalBottomSheetState(ModalBottomSheetValue.Hidden)
    val coroutineScope = rememberCoroutineScope()
    val hudMessenger = rememberHudMessenger()
    val viewModel = koinViewModel<ImportWalletViewModel>()
    val scannerTitle = stringResource(Res.string.ManageAccounts_ImportWallet)
    val openAfterTerms = navigation.rememberTermsAcceptedAction<HSPage>(navigation::slideFromBottom)
    val qrScanner = navigation.rememberQrScannerLauncher(koinInject(), viewModel::handleScannedData)

    // Handle one-shot navigation events from ViewModel
    LaunchedEffect(Unit) {
        viewModel.navigationEvents.collect { event ->
            when (event) {
                is ImportWalletViewModel.NavigationEvent.OpenRestoreFromQr -> {
                    openAfterTerms(
                        RestoreAccountPage(
                            ManageAccountsModule.Input(
                                popOffOnSuccess = popUpToInclusiveId,
                                popOffInclusive = inclusive,
                                prefillWords = event.words,
                                prefillPassphrase = event.passphrase,
                                prefillMoneroHeight = event.moneroHeight,
                                prefillMnemonicLanguageName = event.language?.name
                            )
                        )
                    )
                }

                is ImportWalletViewModel.NavigationEvent.OpenRestoreLocal -> {
                    openAfterTerms(
                        RestoreLocalPage(
                            RestoreLocalPage.Input(
                                popUpToInclusiveId,
                                inclusive,
                                event.backupFilePath,
                                event.fileName
                            )
                        )
                    )
                }
            }
        }
    }

    // Handle error from ViewModel
    viewModel.errorMessage?.let { error ->
        LaunchedEffect(error) {
            hudMessenger.showError(error)
            viewModel.onErrorShown()
        }
    }

    // Handle backup file error
    if (viewModel.backupFileError) {
        LaunchedEffect(Unit) {
            delay(300)
            bottomSheetState.show()
            viewModel.onBackupFileErrorShown()
        }
    }

    val openBackupFilePicker =
        koinInject<BackupFileLauncher>().rememberOpenBackupFile(viewModel::processBackupFile)

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
                    openBackupFilePicker()
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
                    title = stringResource(Res.string.ManageAccounts_ImportWallet),
                    navigationIcon = { HsBackButton(onClick = { navigation.navigateUpSafely() }) }
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .padding(it)
                    .verticalScroll(rememberScrollState())
            ) {
                VSpacer(12.dp)
                ImportOption(
                    title = stringResource(Res.string.ImportWallet_RecoveryPhrase),
                    description = stringResource(Res.string.ImportWallet_RecoveryPhrase_Description),
                    icon = Res.drawable.ic_edit_24,
                    onClick = {
                        openAfterTerms(RestoreAccountPage(ManageAccountsModule.Input(popUpToInclusiveId, inclusive)))
                    }
                )
                VSpacer(12.dp)
                ImportOption(
                    title = stringResource(Res.string.ImportWallet_BackupFile),
                    description = stringResource(Res.string.ImportWallet_BackupFile_Description),
                    icon = Res.drawable.ic_download_24,
                    onClick = openBackupFilePicker,
                )
                VSpacer(12.dp)
                ImportOption(
                    title = stringResource(Res.string.import_wallet_from_qr),
                    description = stringResource(Res.string.import_wallet_from_qr_description),
                    icon = Res.drawable.ic_qr_scan_24,
                    onClick = { qrScanner.open(scannerTitle) }
                )
                VSpacer(12.dp)
            }
        }
    }
}

@Composable
private fun ImportOption(
    title: String,
    description: String,
    icon: DrawableResource,
    onClick: () -> Unit
) {
    CellUniversalLawrenceSection {
        RowUniversal(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalPadding = 24.dp,
            onClick = onClick,
        ) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = ComposeAppTheme.colors.iconSecondary
            )
            HSpacer(16.dp)
            Column {
                headline2_leah(title)
                subhead2_grey(description)
            }
        }
    }
}
