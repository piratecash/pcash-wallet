package cash.p.terminal.modules.contacts.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import cash.p.terminal.core.Caution
import cash.p.terminal.feature.wallet.BackupFileLauncher
import cash.p.terminal.modules.contacts.DeleteContactsPinSettings
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.resources.Alert_TitleWarning
import cash.p.terminal.resources.Button_Cancel
import cash.p.terminal.resources.Contacts_AddAddress_Replace
import cash.p.terminal.resources.Contacts_Backup
import cash.p.terminal.resources.Contacts_Restore
import cash.p.terminal.resources.Contacts_Restore_Warning
import cash.p.terminal.resources.Hud_Text_Done
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Settings_Title
import cash.p.terminal.resources.ic_arrow_right
import cash.p.terminal.resources.icon_warning_2_20
import cash.p.terminal.ui_compose.TransparentModalBottomSheet
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.HsSettingCell
import cash.p.terminal.ui_compose.components.PremiumHeader
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun ContactsSettingsScreen(
    navigation: HSNavigation,
    hasContacts: Boolean,
    backupJson: String,
    backupFileName: String,
    onRestore: (String) -> Unit,
    onClose: () -> Unit
) {
    val hudMessenger = rememberHudMessenger()
    val coroutineScope = rememberCoroutineScope()
    val fileLauncher: BackupFileLauncher = koinInject()
    val dispatcherProvider: DispatcherProvider = koinInject()
    val done = stringResource(Res.string.Hud_Text_Done)
    var showRestoreWarning by remember { mutableStateOf(false) }

    val openRestorePicker = fileLauncher.rememberOpenBackupFile { source, _ ->
        coroutineScope.launch {
            try {
                val json = withContext(dispatcherProvider.io) { source.read() } ?: return@launch
                onRestore(json.decodeToString())
                hudMessenger.showSuccess(done)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                hudMessenger.showError(e.message ?: e::class.simpleName.orEmpty())
            }
        }
    }
    val saveBackup = fileLauncher.rememberSaveBackupFile(
        bytes = backupJson.encodeToByteArray(),
        onSave = { hudMessenger.showSuccess(done) },
        onCancel = {},
        onError = { e -> hudMessenger.showError(e.message ?: e::class.simpleName.orEmpty()) },
    )

    ContactsSettingsContent(
        navigation = navigation,
        onRestoreContacts = {
            if (hasContacts) {
                showRestoreWarning = true
            } else {
                openRestorePicker()
            }
        },
        onBackupContacts = { saveBackup(backupFileName) },
        onClose = onClose
    )

    if (showRestoreWarning) {
        RestoreWarningSheet(
            onConfirm = {
                showRestoreWarning = false
                openRestorePicker()
            },
            onDismiss = { showRestoreWarning = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RestoreWarningSheet(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    TransparentModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        ConfirmationBottomSheet(
            title = stringResource(Res.string.Alert_TitleWarning),
            text = stringResource(Res.string.Contacts_Restore_Warning),
            iconPainter = painterResource(Res.drawable.icon_warning_2_20),
            iconTint = ColorFilter.tint(ComposeAppTheme.colors.statusWarning),
            confirmText = stringResource(Res.string.Contacts_AddAddress_Replace),
            cautionType = Caution.Type.Error,
            cancelText = stringResource(Res.string.Button_Cancel),
            onConfirm = onConfirm,
            onClose = onDismiss
        )
    }
}

@Composable
private fun ContactsSettingsContent(
    navigation: HSNavigation,
    onRestoreContacts: () -> Unit,
    onBackupContacts: () -> Unit,
    onClose: () -> Unit
) {
    Scaffold(
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = {
            AppBar(
                title = stringResource(Res.string.Settings_Title),
                navigationIcon = {
                    HsBackButton(onClick = onClose)
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            VSpacer(12.dp)
            PremiumHeader()
            koinInject<DeleteContactsPinSettings>().Section(navigation)

            VSpacer(16.dp)
            CellUniversalLawrenceSection(
                listOf(
                    {
                        HsSettingCell(
                            title = stringResource(Res.string.Contacts_Restore),
                            arrowPainter = painterResource(Res.drawable.ic_arrow_right),
                            onClick = onRestoreContacts
                        )
                    },
                    {
                        HsSettingCell(
                            title = stringResource(Res.string.Contacts_Backup),
                            arrowPainter = painterResource(Res.drawable.ic_arrow_right),
                            onClick = onBackupContacts
                        )
                    }
                )
            )
            VSpacer(32.dp)
        }
    }
}
