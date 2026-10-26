package cash.p.terminal.modules.blockchainstatus

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cash.p.terminal.resources.Button_Copy
import cash.p.terminal.resources.Button_Share
import cash.p.terminal.resources.Hud_Text_Copied
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.blockchain_status
import cash.p.terminal.resources.blockchain_status_file_saved
import cash.p.terminal.resources.blockchain_status_save_to_file
import cash.p.terminal.resources.error_cannot_create_log_file
import cash.p.terminal.resources.ic_download_20
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryDefault
import cash.p.terminal.ui_compose.components.ButtonPrimaryDefaultWithIcon
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.InfoText
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.subhead1_leah
import cash.p.terminal.ui_compose.components.subhead1_lucian
import cash.p.terminal.ui_compose.components.subhead1_remus
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
internal fun BlockchainStatusScreen(
    viewModel: BlockchainStatusViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboardManager.current
    val hudMessenger = rememberHudMessenger()
    val coroutineScope = rememberCoroutineScope()
    val exporter: StatusReportExporter = koinInject()
    val shareReport = exporter.rememberShareReport()
    val fileSaved = stringResource(Res.string.blockchain_status_file_saved)
    val cannotCreateFile = stringResource(Res.string.error_cannot_create_log_file)
    val copied = stringResource(Res.string.Hud_Text_Copied)
    val saveReport = exporter.rememberSaveReport(
        fileName = if (uiState.statusLoading) null else viewModel.reportFileName,
        buildReport = viewModel::report,
    ) { saved ->
        if (saved) hudMessenger.showSuccess(fileSaved) else hudMessenger.showError(cannotCreateFile)
    }

    Scaffold(
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = {
            AppBar(
                title = "${uiState.blockchainName} ${stringResource(Res.string.blockchain_status)}",
                navigationIcon = {
                    HsBackButton(onClick = onBack)
                },
            )
        }
    ) { paddingValues ->
        if (uiState.loading) {
            Box(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = ComposeAppTheme.colors.textSecondary,
                    strokeWidth = 4.dp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(paddingValues)
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 12.dp)
                    ) {
                        ButtonPrimaryYellow(
                            modifier = Modifier.weight(1f),
                            title = stringResource(Res.string.Button_Copy),
                            enabled = !uiState.statusLoading,
                            onClick = {
                                uiState.statusAsText?.let {
                                    clipboardManager.setText(AnnotatedString(it))
                                    hudMessenger.showSuccess(copied)
                                }
                            }
                        )
                        shareReport?.let { share ->
                            HSpacer(8.dp)
                            ButtonPrimaryDefault(
                                modifier = Modifier.weight(1f),
                                title = stringResource(Res.string.Button_Share),
                                enabled = !uiState.statusLoading && !uiState.shareInProgress,
                                onClick = {
                                    coroutineScope.launch {
                                        try {
                                            viewModel.share(share)
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            hudMessenger.showError(cannotCreateFile)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                item {
                    ButtonPrimaryDefaultWithIcon(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 8.dp),
                        icon = painterResource(Res.drawable.ic_download_20),
                        title = stringResource(Res.string.blockchain_status_save_to_file),
                        enabled = !uiState.statusLoading,
                        onClick = saveReport
                    )
                }

                // Kit Info
                item {
                    KitInfoBlock(uiState.kitVersion, uiState.kitStarted)
                }

                // Status sections (with loading indicator if still loading)
                if (uiState.statusLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = ComposeAppTheme.colors.textSecondary,
                                strokeWidth = 3.dp
                            )
                        }
                    }
                } else {
                    items(uiState.statusSections) { section ->
                        StatusSectionBlock(section)
                    }

                    uiState.sharedSection?.let { section ->
                        item {
                            StatusSectionBlock(section)
                        }
                    }
                }

                // App logs
                logSection(uiState.logGroups)

                item {
                    VSpacer(32.dp)
                }
            }
        }
    }
}

@Composable
private fun KitInfoBlock(kitVersion: String, kitStarted: Boolean) {
    VSpacer(12.dp)
    InfoText(text = "KIT INFO")
    CellUniversalLawrenceSection(
        listOf("version" to kitVersion, "started" to kitStarted)
    ) { (key, _) ->
        RowUniversal(
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            if (key == "version") {
                subhead2_grey(
                    modifier = Modifier.weight(1f),
                    text = "Version"
                )
                subhead1_leah(text = kitVersion)
            } else {
                subhead2_grey(
                    modifier = Modifier.weight(1f),
                    text = "Kit Started"
                )
                if (kitStarted) {
                    subhead1_remus(text = "Yes")
                } else {
                    subhead1_lucian(text = "No")
                }
            }
        }
    }
}

@Composable
fun StatusSectionBlock(section: StatusSection) {
    VSpacer(12.dp)
    InfoText(text = section.title.uppercase())
    CellUniversalLawrenceSection(section.items) { item ->
        RowUniversal(
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            when (item) {
                is StatusItem.KeyValue -> {
                    subhead2_grey(text = item.key)
                    subhead1_leah(
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                        text = item.value,
                        textAlign = TextAlign.End
                    )
                }
                is StatusItem.Nested -> {
                    Column(modifier = Modifier.weight(1f)) {
                        body_leah(text = item.title)
                        item.items.forEach { kv ->
                            Row {
                                subhead2_grey(text = "  ${kv.key}: ")
                                subhead2_grey(text = kv.value)
                            }
                        }
                    }
                }
            }
        }
    }
}
